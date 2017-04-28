package com.xiaojing.registry;

import com.google.common.base.Stopwatch;

import com.xiaojing.registry.common.URL;

import org.apache.commons.lang3.StringUtils;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.imps.CuratorFrameworkState;
import org.apache.curator.framework.recipes.cache.NodeCache;
import org.apache.curator.framework.recipes.cache.NodeCacheListener;
import org.apache.curator.framework.recipes.cache.TreeCache;
import org.apache.curator.framework.recipes.cache.TreeCacheEvent;
import org.apache.curator.framework.recipes.cache.TreeCacheListener;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Created by xiaojing on 16/6/20.
 */
public class ZooKeeperRegistry extends FailbackRegistry {

  private static Logger LOGGER = LoggerFactory.getLogger(ZooKeeperRegistry.class);

  public CuratorFramework curatorFramework;


  public ZooKeeperRegistry(CuratorFramework curatorFramework) {
    super();
    this.curatorFramework = curatorFramework;
    if (curatorFramework.getState() != CuratorFrameworkState.STARTED) {
      curatorFramework.start();//can not be start more than once
    }
  }

  @Override
  protected void doRegister(URL url) {
    String path = url.getUrlPath();
    try {
      curatorFramework.create().creatingParentsIfNeeded().withMode(CreateMode.EPHEMERAL)
          .forPath(path);
      LOGGER.info("register success, url={}, path={}", url, url.getUrlPath());
    } catch (KeeperException.NodeExistsException kne) {
      LOGGER.warn("register success, node exist, url={}, path={}", url, url.getUrlPath());
    } catch (Exception e) {
      LOGGER.error("register fail,url={},,e=", url, e);
      throw new RuntimeException("register fail", e);
    }
  }


  @Override
  public void doCheckNodeExist(URL url) {
    if (null == url) {
      return;
    }
    String nodePath = url.getUrlPath();
    try {
      if (null == curatorFramework.checkExists().forPath(nodePath)) {
        doRegister(url);
      }
    } catch (Exception e) {
      LOGGER.warn("check nodeExist or reRegister error,url={},path={},e=", url, nodePath, e);
    }
  }

  @Override
  protected void doSubscribe(final URL url, final NotifyListener listener) {
    try {
      String path = url.getUrlPath();
      TreeCache treeCache = TreeCache.newBuilder(curatorFramework, path).build();
      treeCache.start();

      TreeCacheListener treeCacheListener = new TreeCacheListener() {
        @Override
        public void childEvent(CuratorFramework curatorFramework, TreeCacheEvent treeCacheEvent)
            throws Exception {
          LOGGER.debug(treeCacheEvent.toString());
          ZooKeeperRegistry.this.notify(url, listener);
        }
      };
      treeCache.getListenable().addListener(treeCacheListener);
      LOGGER.info("add notify Listener to {} success", url);

    } catch (Exception e) {
      LOGGER.error("subscribe url error,url={},e=", url, e);
      throw new RuntimeException("subscribe error", e);
    }
  }

  @Override
  protected void doRegisterWatcher(final URL url, final NotifyListener listener) {
    try {
      String path = url.getUrlPath();
      NodeCache nodeCache = new NodeCache(curatorFramework, path);
      nodeCache.start();

      NodeCacheListener nodeCacheListener = new NodeCacheListener() {
        @Override
        public void nodeChanged() throws Exception {
          LOGGER.debug("url={} node state changed", url);
          ZooKeeperRegistry.this.notify(url, listener);
        }
      };

      nodeCache.getListenable().addListener(nodeCacheListener);
      LOGGER.info("add register watcher Listener to {} success", url);

    } catch (Exception e) {
      LOGGER.error("add register watcher listener error,url={},e=", url, e);
      throw new RuntimeException("add register watcher error", e);
    }
  }

  /***
   * 监听事件只是监听到节点有更改
   * notify操作其实是进行全量pull操作并替换
   *
   * @param url
   * @param listener
   */
  protected void notify(URL url, NotifyListener listener) {
    try {
      listener.doNotify();
    } catch (Exception e) {
      LOGGER.error("notify url node change error,url={},e=", url, e);
    }
  }

  @Override
  protected List<URL> doPull(URL url) throws Exception {

    List<URL> urls = new ArrayList<>();
    Stopwatch stopwatch = Stopwatch.createStarted();
    /**具体到某个host时,只发现此host下的路劲节点*/
    if (StringUtils.isNotBlank(url.getHost())) {
      String hostPath = url.getUrlPath();
      if (null != curatorFramework.checkExists().forPath(hostPath)) {
        try {
          URL serviceUrl = URL.parseUrlPath(hostPath);
          urls.add(serviceUrl);
        } catch (Exception e) {
          LOGGER.warn("parse url path error,path={},e=", hostPath, e);
        }
      }
      return urls;
    }

    /**具体到某个service时,只发现此service下的节点*/
    if (StringUtils.isNotBlank(url.getServiceName())) {
      String servicePath = url.getUrlPath();
      List<String> hostPaths = curatorFramework.getChildren().forPath(servicePath);
      for (String hostPath : hostPaths) {
        hostPath = servicePath + URL.PATH_SEPARATOR + hostPath;
        try {
          URL serviceUrl = URL.parseUrlPath(hostPath);
          urls.add(serviceUrl);
        } catch (Exception e) {
          LOGGER.warn("parse url path error,path={},e=", hostPath, e);
        }
      }
      return urls;
    }

    /**其他情况,一律从根节点往下开始进行服务发现*/
    List<String> servicePaths = curatorFramework.getChildren().forPath(url.getUrlPath());
    for (String servicePath : servicePaths) {
      servicePath = url.getUrlPath() + servicePath;
      List<String> hostPaths = curatorFramework.getChildren().forPath(servicePath);
      for (String hostPath : hostPaths) {
        hostPath = servicePath + URL.PATH_SEPARATOR + hostPath;
        try {
          URL serviceUrl = URL.parseUrlPath(hostPath);
          urls.add(serviceUrl);
        } catch (Exception e) {
          LOGGER.warn("parse url path error,path={},e=", hostPath, e);
        }
      }
    }

    LOGGER.info("do pull urls costTime = " + stopwatch.elapsed(TimeUnit.MILLISECONDS)
                + " ms,size={},urls={}", urls.size(), urls);
    return urls;
  }
}
