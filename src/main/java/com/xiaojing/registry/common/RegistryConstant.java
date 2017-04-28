package com.xiaojing.registry.common;

/**
 * Created by xiaojing on 16/6/22.
 */
public class RegistryConstant {

  /**连接zk相关的配置*/
  public static final String ZK_CONNECT_PATH = "zkConnectPath";
  public static final String SESSION_TIMEOUT_MS = "sessionTimeoutMs";
  public static final String CONNECT_TIMEOUT_MS = "connectTimeoutMs";
  public static final String BASE_SLEEP_TIME_MS = "baseSleepTimeMs";
  public static final String MAX_RETRIES = "maxRetries";
  public static final String NAME_SPACE = "namespace";

  /**配置文件名*/
  public static final String ZK_CONFIG_FILE = "server-registry-zk.yml";

  /**
   * 定时任务的时间配置,目前所用的定时任务有:
   * 1检查服务注册节点的状态
   * 2全量拉去所有服务节点的信息
   */
  public static final long INITIAL_DELAY = 0L;//定时任务的初始等待时间
  public static final long DELAY = 60L;//定时任务的间隔时间
}
