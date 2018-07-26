package com.bonree.brfs.resourceschedule.service;

import java.util.List;

import com.bonree.brfs.common.utils.Pair;
import com.bonree.brfs.resourceschedule.model.LimitServerResource;
import com.bonree.brfs.resourceschedule.model.ResourceModel;

/*****************************************************************************
 * 版权信息：北京博睿宏远数据科技股份有限公司
 * Copyright: Copyright (c) 2007北京博睿宏远数据科技股份有限公司,Inc.All Rights Reserved.
 * 
 * @date 2018年3月16日 上午10:49:46
 * @Author: <a href=mailto:zhucg@bonree.com>朱成岗</a>
 * @Description:可用server接口 
 *****************************************************************************
 */
public interface AvailableServerInterface {
	
	/**
	 * 概述：获取可用server集合
	 * @param scene 场景枚举
	 * @param exceptionServerList 异常server集合
	 * @return
	 * @throws Exception
	 * @user <a href=mailto:zhucg@bonree.com>朱成岗</a>
	 */
	public List<Pair<String, Integer>> selectAvailableServers(int scene, String storageName, List<String> exceptionServerList) throws Exception;
	/**
	 * 概述：获取可用server集合
	 * @param scene 场景枚举
	 * @param exceptionServerList 异常server集合
	 * @return
	 * @throws Exception
	 * @user <a href=mailto:zhucg@bonree.com>朱成岗</a>
	 */
	public List<Pair<String, Integer>> selectAvailableServers(int scene, int snId, List<String> exceptionServerList) throws Exception;
	/**
	 * 概述：设置异常过滤指标
	 * @param limits
	 * @user <a href=mailto:zhucg@bonree.com>朱成岗</a>
	 */
	public void setLimitParameter(LimitServerResource limits);
	/**
	 * 概述：更新资源数据
	 * @param resources key： serverId, resourceModel
	 * @user <a href=mailto:zhucg@bonree.com>朱成岗</a>
	 */
	public void update(ResourceModel resource);
	/***
	 * 概述：添加资源
	 * @param resources
	 * @user <a href=mailto:zhucg@bonree.com>朱成岗</a>
	 */
	public void add(ResourceModel resources);
	/**
	 * 概述：移除资源
	 * @param resource
	 * @user <a href=mailto:zhucg@bonree.com>朱成岗</a>
	 */
	public void remove(ResourceModel resource);
}
