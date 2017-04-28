package com.xiaojing.registry;

import com.xiaojing.registry.common.NetUtils;
import com.xiaojing.registry.common.RegistryConstant;
import com.xiaojing.registry.common.ServerMapUtil;
import com.xiaojing.registry.common.URL;
import com.xiaojing.util.ConfigUtil;

import org.apache.commons.lang3.StringUtils;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Created by xiaojing on 16/6/6.
 */
public class ServerRegistryUtil {

  private static final Logger LOGGER = LoggerFactory.getLogger(ServerRegistryUtil.class);

  /**
   * 注册服务的zk连接的namespace
   */
  private String nameSpace;
  /**
   * 连接zk的host:port
   */
  private String zkConnectPath;
  /***/
  private int sessionTimeoutMs;
  /***/
  private int connectTimeoutMs;
  /***/
  private int baseSleepTimeMs;
  /**
   * 最大重试次数
   */
  private int maxRetries;

  /**
   * 此服务的信息,之所以存在list,是因为有时候一个服务会启动多个端口,提供多种服务
   */
  private List<URL> urls = new ArrayList<>();

  public ZooKeeperRegistry zooKeeperRegistry;

  private static volatile ServerRegistryUtil register;


  private ScheduledExecutorService
      registerPullExecutor =
      Executors.newSingleThreadScheduledExecutor();
  private ScheduledExecutorService
      subscribePullExecutor =
      Executors.newSingleThreadScheduledExecutor();

  private ServerRegistryUtil() {

  }


  public static ServerRegistryUtil getInstance() {
    if (null == register) {
      synchronized (ServerRegistryUtil.class) {
        if (null == register) {
          ServerRegistryUtil instance = new ServerRegistryUtil();
          instance.initZkServerInfo();//初始化连接zk的配置
          CuratorFramework curatorFramework = CuratorFrameworkFactory.builder()
              .connectString(instance.zkConnectPath)
              .connectionTimeoutMs(instance.connectTimeoutMs)
              .sessionTimeoutMs(instance.sessionTimeoutMs)
              .retryPolicy(
                  new ExponentialBackoffRetry(instance.baseSleepTimeMs, instance.maxRetries))
              .namespace(instance.nameSpace)
              .build();
          instance.zooKeeperRegistry = new ZooKeeperRegistry(curatorFramework);
          register = instance;
        }
      }
    }
    return register;
  }


  /**
   * 注册服务,添加定时pull&push
   *
   * @return
   * @throws Exception
   */
  public void register(String serviceName, int port) {

    LOGGER.info("add url from urls, serverInfo: serviceName={},port={} ", serviceName, port);
    String host = "";

    /**不断地去尝试连接zk,直到连接上,获取一个有效的本机地址为止*/
    String[] ipAndPorts = zkConnectPath.split(",");
    for (int i = 0; i < ipAndPorts.length; i++) {
      String ipAndPort = ipAndPorts[i];
      String connIp = StringUtils.substringBefore(ipAndPort, ":");
      int connPort = Integer.parseInt(StringUtils.substringAfter(ipAndPort, ":"));
      InetAddress address = NetUtils.getLocalAddressBySocket(connIp, connPort);
      if (NetUtils.isValidAddress(address)) {
        host = address.getHostAddress();
        break;
      }
    }
    if (StringUtils.isBlank(host) && NetUtils.isValidAddress(NetUtils.getLocalAddress0())) {
      host = NetUtils.getLocalAddress0().getHostAddress();
    }
    if (StringUtils.isNotBlank(host)) {
      URL url = new URL(serviceName, host, port);
      this.urls.add(url);
    }

    /**增加检查自身服务状态是否正常的listener,若不正常,重新注册*/
    for (final URL url : urls) {
      zooKeeperRegistry.registerWatcher(url, new NotifyListener() {
        @Override
        public void doNotify() {
          zooKeeperRegistry.checkNodeExist(url);
        }
      });

    }

    LOGGER.info("begin to register server info,urls={}", urls);
    /**注册服务*/
    for (URL url : urls) {
      zooKeeperRegistry.register(url);
    }

    /**主动pull,检查服务状态是否正常,若不正常,重新注册*/
    registerPullExecutor.scheduleWithFixedDelay(new Runnable() {
      @Override
      public void run() {
        LOGGER.info("begin to check url nodes exist, urls={}", urls);
        for (URL url : urls) {
          zooKeeperRegistry.checkNodeExist(url);
        }
      }
    }, RegistryConstant.INITIAL_DELAY, RegistryConstant.DELAY, TimeUnit.SECONDS);


  }

  public void subscribe(final URL subscribeUrl) {

    /**定时拉取服信息并同步*/
    subscribePullExecutor.scheduleWithFixedDelay(new Runnable() {
      @Override
      public void run() {
        try {
          ServerMapUtil.syncServerMap(zooKeeperRegistry.pull(subscribeUrl));
        } catch (Exception e) {
          LOGGER.warn("sync server map error,subscribeUrl={}, e=", subscribeUrl, e);
        }
      }
    }, RegistryConstant.INITIAL_DELAY, RegistryConstant.DELAY, TimeUnit.SECONDS);

    /**监听到节点变动时,全量替换更新服务信息*/
    zooKeeperRegistry.subscribe(subscribeUrl, new NotifyListener() {
      @Override
      public void doNotify() {
        try {
          /**全量替换ServerMap的信息*/
          ServerMapUtil.syncServerMap(zooKeeperRegistry.doPull(subscribeUrl));
        } catch (Exception e) {
          LOGGER.error("notify url node change error,url={},e=", subscribeUrl, e);
        }
      }
    });
  }

  /**
   * 从配置文件中获取ZK连接相关的信息
   */
  private void initZkServerInfo() {
    try {
      LinkedHashMap<String, Object> linkedHashMap = ConfigUtil.loadYaml(RegistryConstant.ZK_CONFIG_FILE);
      LOGGER.info("load yaml file={},map={}", RegistryConstant.ZK_CONFIG_FILE, linkedHashMap);

      zkConnectPath = (String) linkedHashMap.get(RegistryConstant.ZK_CONNECT_PATH);
      sessionTimeoutMs = (int) linkedHashMap.get(RegistryConstant.SESSION_TIMEOUT_MS);
      connectTimeoutMs = (int) linkedHashMap.get(RegistryConstant.CONNECT_TIMEOUT_MS);
      baseSleepTimeMs = (int) linkedHashMap.get(RegistryConstant.BASE_SLEEP_TIME_MS);
      maxRetries = (int) linkedHashMap.get(RegistryConstant.MAX_RETRIES);
      nameSpace = (String) linkedHashMap.get(RegistryConstant.NAME_SPACE);

    } catch (Exception e) {
      LOGGER.error("initZkServerInfo error, file={}, e=", RegistryConstant.ZK_CONFIG_FILE, e);
      throw new RuntimeException("init zk connect fail,e=", e);
    }
  }

}
