package cn.torna.service;
import java.time.LocalDateTime;

import cn.torna.common.bean.Booleans;
import cn.torna.common.bean.User;
import cn.torna.common.enums.RoleEnum;
import cn.torna.common.support.BaseService;
import cn.torna.common.util.CopyUtil;
import cn.torna.dao.entity.ProjectUser;
import cn.torna.dao.entity.Space;
import cn.torna.dao.entity.SpaceUser;
import cn.torna.dao.entity.UserInfo;
import cn.torna.dao.mapper.SpaceMapper;
import cn.torna.dao.mapper.SpaceUserMapper;
import cn.torna.service.dto.SpaceAddDTO;
import cn.torna.service.dto.SpaceDTO;
import cn.torna.service.dto.SpaceInfoDTO;
import cn.torna.service.dto.SpaceUserInfoDTO;
import cn.torna.service.dto.UserInfoDTO;
import com.gitee.fastmybatis.core.query.Query;
import com.gitee.fastmybatis.core.query.Sort;
import com.gitee.fastmybatis.core.query.param.PageParam;
import com.gitee.fastmybatis.core.support.PageEasyui;
import com.gitee.fastmybatis.core.util.MapperUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author tanghc
 */
@Service
public class SpaceService extends BaseService<Space, SpaceMapper> {

    @Autowired
    private SpaceUserMapper spaceUserMapper;

    @Autowired
    private UserInfoService userInfoService;

    @Transactional(rollbackFor = Exception.class)
    public Space addSpace(SpaceAddDTO spaceAddDTO) {
        String spaceName = spaceAddDTO.getName();
        Query query = new Query().eq("creator_id", spaceAddDTO.getCreatorId())
                .eq("name", spaceName);
        Space existGroup = this.get(query);
        Assert.isNull(existGroup, () -> spaceName + "?????????");
        Space space = new Space();
        space.setName(spaceName);
        space.setCreatorId(spaceAddDTO.getCreatorId());
        space.setCreatorName(spaceAddDTO.getCreatorName());
        space.setModifierId(spaceAddDTO.getCreatorId());
        space.setModifierName(spaceAddDTO.getCreatorName());
        space.setIsCompose(spaceAddDTO.getIsCompose());
        this.save(space);

        // ???????????????
        this.addSpaceUser(space.getId(), spaceAddDTO.getAdminIds(), RoleEnum.ADMIN);
        return space;
    }

    /**
     * ??????????????????
     * @param spaceId ??????id
     * @param userIds ??????id??????
     * @param roleEnum ??????
     */
    public void addSpaceUser(long spaceId, List<Long> userIds, RoleEnum roleEnum) {
        Assert.notEmpty(userIds, () -> "??????????????????");
        Query query = new Query()
                .eq("space_id", spaceId)
                .in("user_id", userIds).setQueryAll(true);
        List<SpaceUser> existUsers = spaceUserMapper.list(query);
        userInfoService.checkExist(existUsers, SpaceUser::getUserId);
        List<SpaceUser> tobeSaveList = userIds.stream()
                .map(userId -> {
                    SpaceUser spaceUser = new SpaceUser();
                    spaceUser.setUserId(userId);
                    spaceUser.setSpaceId(spaceId);
                    spaceUser.setRoleCode(roleEnum.getCode());
                    return spaceUser;
                }).collect(Collectors.toList());
        spaceUserMapper.insertBatch(tobeSaveList);
    }

    /**
     * ????????????????????????
     * @param spaceId spaceId
     * @param userId userId
     * @param roleEnum ??????
     */
    public void updateSpaceUserRole(long spaceId, long userId, RoleEnum roleEnum) {
        Assert.notNull(roleEnum, () -> "??????????????????");
        Map<String, Object> set = new HashMap<>(4);
        set.put("role_code", roleEnum.getCode());
        Query query = new Query().eq("space_id", spaceId)
                .eq("user_id", userId);
        spaceUserMapper.updateByMap(set, query);
    }

    /**
     * ????????????????????????
     * @param spaceId ??????id
     * @param username ????????????
     * @param pageParam ????????????
     * @return
     */
    public PageEasyui<SpaceUserInfoDTO> pageSpaceUser(Long spaceId, String username, PageParam pageParam) {
        PageEasyui spaceUserPageInfo = pageSpaceUser(spaceId, pageParam.getPageIndex(), pageParam.getPageSize());
        if (spaceUserPageInfo == null) {
            return new PageEasyui<>();
        }
        Map<Long, SpaceUser> userIdMap = ((PageEasyui<SpaceUser>)spaceUserPageInfo).getRows().stream()
                .collect(Collectors.toMap(SpaceUser::getUserId, Function.identity()));
        Query query = new Query();
        query.in("id", userIdMap.keySet());
        if (StringUtils.hasText(username)) {
            query.and(q -> q.like("username", username)
                    .orLike("nickname", username)
                    .orLike("email", username)
            );
        }
        List<UserInfo> userInfos = userInfoService.list(query);
        List<SpaceUserInfoDTO> spaceUserInfoDTOS = CopyUtil.copyList(userInfos, SpaceUserInfoDTO::new);
        // ??????????????????
        spaceUserInfoDTOS.forEach(userInfoDTO -> {
            SpaceUser spaceUser = userIdMap.get(userInfoDTO.getId());
            userInfoDTO.setGmtCreate(spaceUser.getGmtCreate());
            userInfoDTO.setRoleCode(spaceUser.getRoleCode());
        });
        spaceUserInfoDTOS.sort(Comparator.comparing(SpaceUserInfoDTO::getGmtCreate).reversed());
        spaceUserPageInfo.setList(spaceUserInfoDTOS);
        return spaceUserPageInfo;
    }

    /**
     * ??????????????????
     * @param spaceId
     * @param username ???????????? like 'xx%'
     * @return
     */
    public List<UserInfoDTO> searchSpaceUser(long spaceId, String username) {
        if (StringUtils.isEmpty(username)) {
            return Collections.emptyList();
        }
        List<SpaceUser> spaceUsers = listSpaceUser(spaceId);
        Map<Long, SpaceUser> userIdMap = spaceUsers.stream()
                .collect(Collectors.toMap(SpaceUser::getUserId, Function.identity()));
        Query query = new Query();
        query.in("id", userIdMap.keySet());
        query.and(q -> q.like("username", username)
                    .orLike("nickname", username)
                    .orLike("email", username)
            );

        List<UserInfo> userInfoList = userInfoService.list(query);
        return CopyUtil.copyList(userInfoList, UserInfoDTO::new);
    }

    /**
     * ??????????????????
     * @param spaceId
     * @return
     */
    public List<UserInfoDTO> listAllSpaceUser(long spaceId) {
        List<SpaceUser> spaceUsers = listSpaceUser(spaceId);
        Map<Long, SpaceUser> userIdMap = spaceUsers.stream()
                .collect(Collectors.toMap(SpaceUser::getUserId, Function.identity()));
        Query query = new Query();
        query.in("id", userIdMap.keySet())
                .orderby("id", Sort.DESC);

        List<UserInfo> userInfoList = userInfoService.list(query);
        return CopyUtil.copyList(userInfoList, UserInfoDTO::new);
    }

    public SpaceInfoDTO getSpaceInfo(long spaceId) {
        Space space = getById(spaceId);
        SpaceInfoDTO spaceInfoDTO = CopyUtil.copyBean(space, SpaceInfoDTO::new);
        List<UserInfoDTO> leaders = this.listSpaceAdmin(spaceId);
        spaceInfoDTO.setLeaders(leaders);
        return spaceInfoDTO;
    }

    /**
     * ??????????????????
     * @param spaceId ??????id
     * @param userId ????????????
     */
    public void removeMember(long spaceId, long userId) {
        Query query = new Query()
                .eq("space_id", spaceId)
                .eq("user_id", userId);
        spaceUserMapper.forceDeleteByQuery(query);
    }

    public SpaceUser getSpaceUser(Long spaceId, Long userId) {
        if (spaceId == null || userId == null) {
            return null;
        }
        Query query = new Query().eq("space_id", spaceId)
                .eq("user_id", userId);
        return spaceUserMapper.getByQuery(query);
    }


    /**
     * ???????????????????????????
     * @param spaceId
     * @return
     */
    public List<UserInfoDTO> listSpaceAdmin(long spaceId) {
        Query query = new Query()
                .eq("space_id", spaceId)
                .eq("role_code", RoleEnum.ADMIN.getCode())
                .setQueryAll(true);
        List<SpaceUser> spaceLeaders = spaceUserMapper.list(query);
        List<Long> adminIds = CopyUtil.copyList(spaceLeaders, SpaceUser::getUserId);
        return userInfoService.listUserInfo(adminIds);
    }

    /**
     * ???????????????????????????id
     * @param spaceId ??????
     * @return ????????????id
     */
    public List<Long> listSpaceAdminId(long spaceId) {
        return listSpaceAdmin(spaceId)
                .stream()
                .map(UserInfoDTO::getId)
                .collect(Collectors.toList());
    }

    public List<SpaceUser> listSpaceUser(Long spaceId) {
        if (spaceId == null) {
            return Collections.emptyList();
        }
        Query query = new Query()
                .eq("space_id", spaceId)
                .orderby("gmt_create", Sort.DESC);
        return spaceUserMapper.list(query);
    }

    public PageEasyui<SpaceUser> pageSpaceUser(Long spaceId, int pageIndex, int pageSize) {
        if (spaceId == null) {
            return null;
        }
        Query query = new Query()
                .eq("space_id", spaceId)
                .orderby("gmt_create", Sort.DESC)
                .page(pageIndex, pageSize);
        return MapperUtil.queryForEasyuiDatagrid(spaceUserMapper, query);
    }

    /**
     * ???????????????????????????
     * @param userId ??????
     * @return ??????????????????
     */
    public List<SpaceUser> listUserSpace(long userId) {
        return spaceUserMapper.listByColumn("user_id", userId);
    }

    /**
     * ?????????????????????????????????
     * @param user ??????
     * @return ??????????????????
     */
    public List<SpaceDTO> listSpace(User user) {
        if (user.isSuperAdmin()) {
            return this.listAll(SpaceDTO::new);
        }
        List<Long> spaceIds;
        List<SpaceUser> spaceUserList = spaceUserMapper.listByColumn("user_id", user.getUserId());
        if (CollectionUtils.isEmpty(spaceUserList)) {
            return Collections.emptyList();
        }
        spaceIds = spaceUserList.stream()
                .map(SpaceUser::getSpaceId)
                .collect(Collectors.toList());

        Query query = new Query()
                .in("id", spaceIds);
        List<Space> spaces = this.listAll(query);
        return CopyUtil.copyList(spaces, SpaceDTO::new);
    }

    /**
     * ??????????????????????????????
     * @param spaceId ??????id
     */
    public void cleanDeletedUser(long spaceId) {
        Query query = new Query()
                .eq("space_id", spaceId)
                .eq("is_deleted", Booleans.TRUE);
        spaceUserMapper.forceDeleteByQuery(query);
    }

    /**
     * ??????????????????????????????
     * @param projectUsers ????????????
     * @param spaceIdNew ????????????
     */
    public void transformSpaceUser(List<ProjectUser> projectUsers, long spaceIdNew) {
        this.cleanDeletedUser(spaceIdNew);
        List<SpaceUser> spaceUsersNew = this.listSpaceUser(spaceIdNew);
        List<Long> destSpaceUserIds = spaceUsersNew.stream()
                .map(SpaceUser::getUserId)
                .collect(Collectors.toList());

        List<SpaceUser> tobeSaveList = new ArrayList<>();

        for (ProjectUser projectUser : projectUsers) {
            Long userId = projectUser.getUserId();
            // ?????????????????????????????????
            if (!destSpaceUserIds.contains(userId)) {
                SpaceUser spaceUser = new SpaceUser();
                spaceUser.setUserId(userId);
                spaceUser.setSpaceId(spaceIdNew);
                spaceUser.setRoleCode(projectUser.getRoleCode());
                tobeSaveList.add(spaceUser);
            }
        }
        spaceUserMapper.saveBatchIgnoreNull(tobeSaveList);
    }

}