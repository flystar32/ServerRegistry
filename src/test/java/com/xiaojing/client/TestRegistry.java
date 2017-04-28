package com.xiaojing.client;

import com.xiaojing.registry.ServerRegistryUtil;
import com.xiaojing.registry.common.ServerMapUtil;
import com.xiaojing.registry.common.URL;

import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

/**
 * Created by xiaojing on 17/4/28.
 */
public class TestRegistry {


  @Test
  public void testRegistry() throws Exception {
    ServerRegistryUtil serverRegistryUtil = ServerRegistryUtil.getInstance();

    serverRegistryUtil.register("hello1", 9999);
    serverRegistryUtil.register("hello2", 9999);
    serverRegistryUtil.register("hello3", 9999);
    serverRegistryUtil.register("hello4", 9999);

    serverRegistryUtil.subscribe(new URL());//订阅所有的服务

    TimeUnit.SECONDS.sleep(5);

    Assert.assertEquals(4, ServerMapUtil.serverMap.size());
  }

}
