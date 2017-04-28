## 解决的问题
1、原有的方式是所有的服务的ip和port都是在配置文件中写死的。需要解决扩容之后需要修改文件手动上线的问题，减少开发和维护成本。
2、同时，也希望服务出现故障不能服务时，上游能够及时发现，而不需要修改配置文件。

## 为什么选zk
1、zk提供了临时节点，当client与server的连接断开后，临时节点自动消失。
2、zk提供了注册watcher和notify机制，可以及时地通知各客户端服务节点的变动信息。

## 关键点
#### zk的路径和data
服务节点内容包含哪些信息，哪些是需要关心的，哪些放在path里，哪些放在data中。
ip、port是肯定是需要的，生产集群和灰度集群区分开来，环境也是重要的一个属性。
注册时候zkpath是怎样个结构，我们将环境放在path里了，zkpath使用的是这种方式/xxxx/server-registry/service-department/enviroment/service-name/ip:port，权重放在节点的内容里，但是目前我们的业务场景还没有使用到权重这个特性。
在这过程中，也参考了dubbo和motan的实现，很难说找到一个很完美的用法，environment放在路径里是因为我们的环境本身就是放在配置文件中的，而且目前zk只关心了路径而没使用节点data，实现起来简单方便，并满足目前需求。
#### 顺序问题
一个服务在启动前，必须先做服务发现，然后启动服务，服务启动完后才能注册。
同时，服务的发现和注册，需要使用后台线程去做，连接zk出现异常不能阻塞服务启动主流程。
关于服务发现后的注册，还掉进了一个坑里，详见[java内存可见性](http://www.cnblogs.com/flystar32/p/6246809.html)
在不阻塞主流程方面，我们参考dubbo和motan的方式，使用failback策略。
后台线程不断重试未执行成功的内容。维护了一个failSet，在执行时，先从set中remove，然后再执行操作，遇到导致了执行失败的异常，则将其重新加入到failSet中，等待后台线程下一次重试执行。
说到连zk的异常，特别地，绝对不能在synchronized的代码中执行连接zk并使用阻塞方法获取数据的逻辑。
比如在com.wandoulabs.jodis的RoundRobinCodisPool中，构造方法里调用了连ZK的阻塞方法，`watcher.start(StartMode.BUILD_INITIAL_CACHE)`。在我们的其他组件的代码中。想构造一个pool的单例，在synchronized的代码块中new了一个RoundRobinCodisPool，简直就是灾难。


#### notify丢失场景
Curator自带的服务注册工具curator-x-discovery的处理方式是忽视这个notify的丢失。当session丢失，导致本地的临时节点消失时，处理方式是关心ConnectionState变动的事件，大不了出现连接状态出现变化的时候再重新拉一遍和注册一遍。
我们这里使用的是定时线程检查的策略。包含了每分钟主动全量拉取一次关心的节点和每分钟检查一次自身节点是否还存在。

在zk的监听事件通知的原理中，是先删除notify的watcher，然后再发送notify消息的，所以说notify只要出现了丢失的话，就只能是丢失了，没有办法确保notify一定收到的。

    public Set<Watcher> triggerWatch(String path, EventType type, Set<Watcher> supress) {
        WatchedEvent e = new WatchedEvent(type, KeeperState.SyncConnected, path);
        HashSet<Watcher> watchers;
        synchronized (this) {
            watchers = watchTable.remove(path);
            if (watchers == null || watchers.isEmpty()) {
                if (LOG.isTraceEnabled()) {
                    ZooTrace.logTraceMessage(LOG, ZooTrace.EVENT_DELIVERY_TRACE_MASK, "No watchers for " + path);
                }
                return null;
            }
            for (Watcher w : watchers) {
                HashSet<String> paths = watch2Paths.get(w);
                if (paths != null) {
                    paths.remove(path);
                }
            }
        }
        for (Watcher w : watchers) {
            if (supress != null && supress.contains(w)) {
                continue;
            }
            w.process(e);
        }
        return watchers;
    }

#### 如何获取本机ip
详见[如何获取本机IP](http://www.cnblogs.com/flystar32/p/how_to_get_local_ip.html)

#### zk server不可用时的降级策略
zk不可用时的时候服务可能处于两种状态，服务正在启动，或者是服务已经运行一段时间了。
当启动时候，直接连不上zk，使用本地配置文件进行兜底。
使用过程中zk断开，则会导致每分钟更新很检查时发现zk server异常，此类异常会被catch住并warn，而不影响主流程。
当发现没有任何节点时，不会更新节点信息，而是使用内存中的旧数据。

#### client session抖动
在服务运行的过程中，client节点与zk server的连接发生抖动。
对于发现功能来说，处理的方式与上文中server不可用的处理一致，都是使用本地缓存。
对于注册功能来说，等待client再一次连接上时，会主动重新注册。
curator的连接重试机制，和watcher再注册机制非常出色，很好地帮我们解决了这个问题。

#### 服务上线
服务上线的流程，一般都是先删除旧文件，再拷贝新文件，然后停止服务，最后再启动服务。
在停止服务到服务完全启动好这一段时间，服务处于不可以状态，上游调用此服务失败时的重试策略是，如果当前的请求出现了IOException，则换一台机器重试一次，并将本次使用的机器标记为不可用，不可用时间随着连续失败的次数上升而增加，分别是1s，5s，60s。
但是在zk临时节点的模型中，都是等待zk的session timeout才会下线机器的，而这个session timeout是由我们主动设置的，default值为60s，会有很长的滞后性。在这种情况下，会出现服务已经启动了，原有临时节点还没消失的情况，这也从另一面说明了主动检查自身节点存在的必要性。当然可以对自身所对应的path注册watcher来解决，但是watcher会存在消息丢失的问题。
我们使用的是5s，但是设置得小一些也不是解决此问题的根本办法。所以我们还在服务的shutdown hook里增加了服务反注册方法，主动去断开这个zk client的session，而不是消极地等待session timeout，这样能让监控中的报警少一些。


## 关键点
[talk is cheap,code is here](https://github.com/flystar32/ServerRegistry)

## 问题

#### 网络分区
  当zk出现网络分区后，zk集群会被分成两部分，即满足最小节点数的部分和不满足最小节点数的部分。
  如果leader被分在了满足最小节点数的那一侧，那么这一侧的所有写请求和读请求都能正常工作。但是另一侧的分区，由于没有leader，所有的读和写请求都会返回error。
  如果leader被分在了不满足最小节点数的那一侧，这一侧的分区会发现此小集群的节点数量已经不足最小节点数了，对于写请求来说，zab需要足够多的节点确认来提交，所以所有的写请求都会失败，对于读请求来说，会存在着一个时间窗口使得读请求返回了脏数据，注意，这并不违反zk的一致性保证的原则，因为对于自身没有修改过数据的情况来说，是允许读到脏数据的。而在满足最小节点数的那一侧，节点们发现没有leader后会重新选举一个leader，当网络分区的问题解决后，另一侧的节点会被重新加入到这一侧，并同步最新的数据。


#### AP & CP
zookeeper设计的原则就是遵循了CAP中的CP，包括一致性和分区容错性。但是服务注册发现其实要求系统是AP的，对于一致性的要求并不是那么强烈，所以从某种意义上来说，使用zookeeper来做服务注册与发现，从一开始就南辕北辙。因为服务注册与发现首要保证的就是可用性。


#### trade off
使用何种工具，一定要基于自身的实际情况来决定。
当然也并不是如刚才说用zk做服务发现是南辕北辙说的那么绝对，应该分情况讨论，在我们的系统中，所有的机器，包括zk和各个服务，都是在内网的，出现网络分区的情况绝少，所以选择使用zk也能满足需求。

## 后记
这是我在16年5月份写的一个小项目，也是我自己第一次独立负责一个基础组件的开发，这个组件属于我和mentor一起开发的API gateway的一部分。非常感谢mentor能给我这样一个机会，并不断地提供辅导。在这个过程中，感觉编程就是不断地和各种异常情况在做斗争。

## 参考文档
[Open-Source Service Discovery](http://jasonwilder.com/blog/2014/02/04/service-discovery-in-the-cloud)
[Eureka! Why You Shouldn’t Use ZooKeeper for Service Discovery](https://tech.knewton.com/blog/2014/12/eureka-shouldnt-use-zookeeper-service-discovery/)
[How ZooKeeper Handles Failure Scenarios](https://wiki.apache.org/hadoop/ZooKeeper/FailureScenarios)