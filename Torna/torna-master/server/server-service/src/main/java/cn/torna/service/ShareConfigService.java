package cn.torna.service;

import cn.torna.common.bean.Booleans;
import cn.torna.common.bean.User;
import cn.torna.common.enums.ShareConfigTypeEnum;
import cn.torna.common.enums.StatusEnum;
import cn.torna.common.exception.BizException;
import cn.torna.common.support.BaseService;
import cn.torna.common.util.PasswordUtil;
import cn.torna.dao.entity.DocInfo;
import cn.torna.dao.entity.ModuleEnvironment;
import cn.torna.dao.entity.ShareConfig;
import cn.torna.dao.entity.ShareContent;
import cn.torna.dao.entity.ShareEnvironment;
import cn.torna.dao.mapper.ShareConfigMapper;
import cn.torna.service.dto.DocInfoDTO;
import cn.torna.service.dto.ModuleEnvironmentDTO;
import cn.torna.service.dto.ShareConfigDTO;
import cn.torna.service.dto.ShareDocDTO;
import com.gitee.fastmybatis.core.query.Query;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author tanghc
 */
@Service
public class ShareConfigService extends BaseService<ShareConfig, ShareConfigMapper> {

    @Autowired
    private ShareContentService shareContentService;
    @Autowired
    private ShareEnvironmentService shareEnvironmentService;
    @Autowired
    private ModuleEnvironmentService moduleEnvironmentService;
    @Autowired
    private DocInfoService docInfoService;

    @Transactional(rollbackFor = Exception.class)
    public void add(ShareConfigDTO shareConfigDTO, User user) {
        ShareConfig shareConfig = new ShareConfig();
        shareConfig.setModuleId(shareConfigDTO.getModuleId());
        shareConfig.setType(shareConfigDTO.getType());
        shareConfig.setIsAll(shareConfigDTO.getIsAll());
        shareConfig.setRemark(shareConfigDTO.getRemark());
        shareConfig.setCreatorName(user.getNickname());
        shareConfig.setStatus(StatusEnum.ENABLE.getStatus());
        shareConfig.setIsShowDebug(shareConfigDTO.getIsShowDebug());
        if (shareConfigDTO.getType() == ShareConfigTypeEnum.ENCRYPT.getType()) {
            String pwd = PasswordUtil.getRandomSimplePassword(4);
            shareConfig.setPassword(pwd);
        } else {
            shareConfig.setPassword("");
        }
        this.save(shareConfig);
        this.saveContent(shareConfig, shareConfigDTO);
        this.saveEnvironment(shareConfig, shareConfigDTO);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(ShareConfigDTO shareConfigDTO) {
        ShareConfig shareConfig = this.getById(shareConfigDTO.getId());
        shareConfig.setModuleId(shareConfigDTO.getModuleId());
        shareConfig.setType(shareConfigDTO.getType());
        shareConfig.setIsAll(shareConfigDTO.getIsAll());
        shareConfig.setRemark(shareConfigDTO.getRemark());
        shareConfig.setIsShowDebug(shareConfigDTO.getIsShowDebug());
        shareConfig.setIsAllSelectedDebug(shareConfigDTO.getIsAllSelectedDebug());
        if (shareConfigDTO.getType() == ShareConfigTypeEnum.ENCRYPT.getType()) {
            if (StringUtils.isEmpty(shareConfig.getPassword())) {
                String pwd = PasswordUtil.getRandomSimplePassword(4);
                shareConfig.setPassword(pwd);
            }
        } else {
            shareConfig.setPassword("");
        }
        this.update(shareConfig);
        this.saveContent(shareConfig, shareConfigDTO);
        this.saveEnvironment(shareConfig, shareConfigDTO);
    }

    /**
     *  ??????????????????
     *
     * @author Joker
     * @param shareConfig
     * @param shareConfigDTO
     * @since 1.0.0
     * @return
     */
    private void saveEnvironment(ShareConfig shareConfig, ShareConfigDTO shareConfigDTO) {
        // ??????,??????,??????????????????????????????
        this.deleteEnvironment(shareConfig);
        if (Objects.equals(shareConfig.getIsShowDebug(), Booleans.FALSE)) {
            return;
        }

        // ???????????????????????????????????????
        List<Long> moduleEnvironmentIdSet = shareConfigDTO.getModuleEnvironmentIdList();
        if (CollectionUtils.isEmpty(moduleEnvironmentIdSet)) {
            return;
        }

        List<ShareEnvironment> environmentList = moduleEnvironmentIdSet.stream().map(envId -> {
            ShareEnvironment shareEnvironment = new ShareEnvironment();
            shareEnvironment.setModuleEnvironmentId(envId);
            shareEnvironment.setShareConfigId(shareConfig.getId());
            return shareEnvironment;
        }).collect(Collectors.toList());

        shareEnvironmentService.saveBatch(environmentList);
    }

    private void deleteEnvironment(ShareConfig shareConfig) {
        shareEnvironmentService.getMapper().deleteByQuery(new Query().eq("share_config_id", shareConfig.getId()));
    }

    public List<ShareContent> listContent(long id) {
        return shareContentService.list("share_config_id", id);
    }

    public List<ShareDocDTO> listShareDocIds(long id) {
        List<ShareContent> shareContents = listContent(id);
        List<ShareDocDTO> list = new ArrayList<>(shareContents.size());
        // ??????????????????
        List<ShareDocDTO> shareFolderDocIds = shareContents.stream().filter(shareContent -> shareContent.getIsShareFolder() == Booleans.TRUE)
                .map(shareContent -> new ShareDocDTO(shareContent.getDocId(), shareContent.getIsShareFolder()))
                .collect(Collectors.toList());
        list.addAll(shareFolderDocIds);

        // ??????????????????id
        List<Long> docIdList = listDocId(shareContents);
        List<ShareDocDTO> docIds = docIdList
                .stream()
                .map(docId -> new ShareDocDTO(docId, Booleans.FALSE))
                .collect(Collectors.toList());

        list.addAll(docIds);

        return list;
    }

    private List<Long> listDocId(List<ShareContent> shareContents) {
        List<Long> docIds = shareContents.stream()
                .filter(shareContent -> shareContent.getIsShareFolder() == Booleans.FALSE)
                .map(ShareContent::getDocId)
                .collect(Collectors.toList());
        return docInfoService.listDocByIds(docIds)
                .stream()
                .filter(docInfo -> docInfo.getIsFolder() == Booleans.FALSE)
                .map(DocInfo::getId)
                .collect(Collectors.toList());
    }

    public List<ShareEnvironment> listEnvironment(long id) {

        ShareConfig config = this.getMapper().getById(id);
        Byte isShowDebug = config.getIsShowDebug();
        Byte isAllSelectedDebug = config.getIsAllSelectedDebug();

        if (Booleans.TRUE == isShowDebug && Booleans.TRUE == isAllSelectedDebug) {
            Long moduleId = config.getModuleId();
            List<ModuleEnvironment> moduleEnvironments = moduleEnvironmentService.listModuleEnvironment(moduleId);
            return moduleEnvironments.stream().map(env -> new ShareEnvironment(null, id, env.getId())).collect(Collectors.toList());
        }
        return shareEnvironmentService.list("share_config_id", id);
    }


    private void saveContent(ShareConfig shareConfig, ShareConfigDTO shareConfigDTO) {
        // ??????????????????
        this.deleteContent(shareConfig);
        if (shareConfigDTO.getIsAll() == Booleans.TRUE) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<ShareContent> tobeSave = shareConfigDTO.getContent()
                .stream()
                .map(content -> {
                    ShareContent shareContent = new ShareContent();
                    Long parentId = content.getParentId();
                    if (parentId == null) {
                        parentId = 0L;
                    }
                    shareContent.setDocId(content.getDocId());
                    shareContent.setParentId(parentId);
                    shareContent.setIsShareFolder(content.getIsShareFolder());
                    shareContent.setShareConfigId(shareConfig.getId());
                    shareContent.setIsDeleted(Booleans.FALSE);
                    shareContent.setGmtCreate(now);
                    shareContent.setGmtModified(now);
                    return shareContent;
                })
                .collect(Collectors.toList());
        if (tobeSave.isEmpty()) {
            return;
        }
        shareContentService.getMapper().saveBatch(tobeSave);
    }

    private void deleteContent(ShareConfig shareConfig) {
        Query query = new Query()
                .eq("share_config_id", shareConfig.getId());
        shareContentService.getMapper().forceDeleteByQuery(query);
    }

    /**
     * ?????????????????????????????????
     * @param docId
     * @param shareConfigId
     * @return
     */
    public DocInfoDTO getShareDocDetail(long docId, long shareConfigId) {
        DocInfoDTO docDetail = docInfoService.getDocDetailView(docId);
        ShareConfig config = this.getMapper().getById(shareConfigId);
        Optional.ofNullable(docDetail).orElseThrow(() -> new BizException("??????????????????"));
        Optional.ofNullable(config).orElseThrow(() -> new BizException("????????????????????????"));

        List<ModuleEnvironmentDTO> debugEnvs = docDetail.getDebugEnvs();
        if (CollectionUtils.isEmpty(debugEnvs)) {
            return docDetail;
        }

        List<ShareEnvironment> shareEnvironments = this.listEnvironment(shareConfigId);
        if (CollectionUtils.isEmpty(shareEnvironments)) {
            docDetail.setDebugEnvs(Collections.emptyList());
            return docDetail;
        }
        Set<Long> envIdSet = shareEnvironments.stream().map(ShareEnvironment::getModuleEnvironmentId).collect(Collectors.toSet());
        List<ModuleEnvironmentDTO> dtoList = debugEnvs.stream().filter(env -> envIdSet.contains(env.getId())).collect(Collectors.toList());
        docDetail.setDebugEnvs(dtoList);
        return docDetail;
    }



}