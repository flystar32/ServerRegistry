package com.xiaojing.registry;

import com.google.common.collect.Sets;

import com.xiaojing.registry.common.URL;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Created by xiaojing on 16/6/20.
 */
public abstract class FailbackRegistry implements RegistryService, DiscoveryService {

  private static final Logger LOGGER = LoggerFactory.getLogger(FailbackRegistry.class);

  public static final long retryPeriod = 5L;

  private Set<URL> failedRegistered = Sets.newConcurrentHashSet();
  private Map<URL, NotifyListener> failedSubscribed = new ConcurrentHashMap<>();
  private Map<URL, NotifyListener> failedRegisteredWatcher = new ConcurrentHashMap<>();
  private static ScheduledExecutorService
      retryExecutor =
      Executors.newSingleThreadScheduledExecutor();


  public FailbackRegistry() {
    /**
     * 在构造方法中初始化重试线程,定制执行重试
     */
    retryExecutor.scheduleWithFixedDelay(new Runnable() {
      @Override
      public void run() {
        try {
          retry();
        } catch (Exception e) {
          LOGGER.warn("fail back registry error,failedRegistered={},e=", failedRegistered, e);
        }

      }
    }, retryPeriod, retryPeriod, TimeUnit.SECONDS);
  }


  @Override
  public void subscribe(URL url, NotifyListener listener) {
    failedSubscribed.remove(url);
    try {
      if (url == null || listener == null) {
        LOGGER.warn("invalid url or listener,url={},listener={}", url, listener);
        return;
      }
      doSubscribe(url, listener);
    } catch (Exception e) {
      failedSubscribed.put(url, listener);
    }
  }

  @Override
  public List<URL> pull(URL url) throws Exception {
    if (null == url) {
      LOGGER.error("pull all subscribed urls fail.subscribeUrl={}", url);
      throw new IllegalArgumentException("can't subscribe null url");
    }
    return doPull(url);
  }

  @Override
  public void registerWatcher(URL url, NotifyListener listener) {
    failedRegisteredWatcher.remove(url);
    try {
      if (url == null || listener == null) {
        LOGGER.warn("invalid url or listener,url={},listener={}", url, listener);
        return;
      }
      doRegisterWatcher(url, listener);
    } catch (Exception e) {
      failedRegisteredWatcher.put(url, listener);
    }

  }

  @Override
  public void register(URL url) {
    failedRegistered.remove(url);
    try {
      if (url == null || url.getPort() == 0 || StringUtils.isBlank(url.getServiceName())) {
        LOGGER.warn("url information uncompleted,url={}", url);
        return;
      }
      LOGGER.info("begin to register url={},path={}", url, url.getUrlPath());
      doRegister(url);
    } catch (Exception e) {
      failedRegistered.add(url);//failedRegistered是一个Set,多次添加无副作用
      LOGGER.warn("add {} to failed registered set,failedRegistered={}", url, failedRegistered);
    }
  }

  @Override
  public void checkNodeExist(URL url) {
    if (url == null) {
      LOGGER.warn("check node exist warn,url=null");
      return;
    }
    LOGGER.info("begin to check node exist url={}", url);
    doCheckNodeExist(url);
  }


  /**
   * 当与中间件通信的过程中,失败并不影响后续的主流程
   * 而是会通过重试线程重复执行失败的set里的操作来,直到成功为止
   */
  private void retry() {

    /**重试失败的注册*/
    if (!failedRegistered.isEmpty()) {
      Set<URL> failed = new HashSet<URL>(failedRegistered);
      LOGGER.info("fail back auto retry register,{}", failed);
      for (URL url : failed) {
        try {
          doRegister(url);
          failedRegistered.remove(url);
        } catch (Exception e) {
          LOGGER.warn("fail to register,url={},e=", url, e);
        }
      }
    }

    /**重试失败的订阅*/
    if (!failedSubscribed.isEmpty()) {
      ConcurrentHashMap<URL, NotifyListener> failed = new ConcurrentHashMap<>(failedSubscribed);
      LOGGER.info("fail back auto retry subscribe,{}", failed.keySet());
      for (URL url : failed.keySet()) {
        try {
          NotifyListener listener = failed.get(url);
          doSubscribe(url, listener);
          failedSubscribed.remove(url);
        } catch (Exception e) {
          LOGGER.warn("fail to subscribe, url={}, e=", url, e);
        }
      }
    }

    /**重试失败的注册watcher*/
    if (!failedRegisteredWatcher.isEmpty()) {
      ConcurrentHashMap<URL, NotifyListener>
          failed =
          new ConcurrentHashMap<>(failedRegisteredWatcher);
      LOGGER.info("fail back auto retry register watcher,{}", failed.keySet());
      for (URL url : failed.keySet()) {
        try {
          NotifyListener listener = failed.get(url);
          doRegisterWatcher(url, listener);
          failedRegisteredWatcher.remove(url);
        } catch (Exception e) {
          LOGGER.warn("fail to register watcher, url={}, e=", url, e);
        }
      }
    }

  }

  protected abstract void doRegister(URL url);

  protected abstract void doSubscribe(URL url, NotifyListener listener);

  protected abstract void doRegisterWatcher(URL url, NotifyListener listener);

  protected abstract List<URL> doPull(URL url) throws Exception;

  protected abstract void doCheckNodeExist(URL url);


}
