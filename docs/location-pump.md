# 定位投递架构与真机排查

## v1.0.4 的调整

这次改动针对“地图预览能移动，开始记录后定位停止”的投递链路风险，不代表已经确认所有手机、所有跑步应用的根因。

- `MockLocationService`：只接收启停/配速指令、维护前台通知和 WakeLock、向 UI 广播状态。
- `MockLocationPump`：独立前台优先级 HandlerThread，默认 200ms，构造参数支持 100ms。按绝对 uptime deadline 调度；错过时跳过过期槽位，不突发补发。
- `TrajectoryInterpolator`：仅由定位泵线程访问，按 elapsed realtime 毫秒积分。细分积分步长保持 100ms；航向平滑、终点静止判定按时间计算。重复/倒退时间戳返回上一观测，不重复推进。
- `LocationSampleFactory`：每次产生新的 Location、wall time、elapsedRealtimeNanos、速度/航向及传感器元数据。元数据变化按经过时间缩放，避免 5Hz 放大噪声。各通道复制同一个观测，避免同一时刻输出不同坐标。
- GPS/Network：同步投递、分通道捕获错误；发现禁用或写入失败后重新 remove/add/enable，失败采用有上限的退避，不终止跑步。
- Android platform fused：独立 HandlerThread、单槽邮箱，与主泵隔离。
- Google Play FLP：独立 HandlerThread，异步 Task，最多一个在途操作和一个最新待发位置。明确失败后退避 250/500/1000/2000/5000ms；实际位置成功投递后复位退避。
- 停止：立刻撤销投递许可，异步清理 Provider 和 FLP 模拟模式，不再减速跑动。Provider 所有权检查避免旧服务的清理删除新服务的 Provider。

保留现有地图、选点、开放/闭合路线、循环跑、坐标转换、速度约束和轨迹噪声逻辑；没有移除或隐藏 Android 的模拟位置标记。

## 两个边界

1. Android HandlerThread 不是硬实时调度器。200ms 是目标周期，Binder/系统负载可能产生延迟。原算法仍保留单次最大 5 秒积分保护，长时间被系统挂起后不会一次追赶全部距离。
2. Google Task 没有此处可用的取消接口。超过 2 秒记录超时诊断，但不叠加新的在途请求；待任务完成后发送最新观测或按失败退避。如果底层任务永久不返回，该 FLP 通道不能保证自动恢复，GPS/Network 不受影响。关闭后还会处理迟到的开启模拟模式回调，尝试关闭模拟模式。

## 日志

在连接发生问题的手机后，从开始模拟之前采集：

```shell
adb logcat -v threadtime -s MockLocationService MockLocationPump MockLocationPush MockGoogleFlp > virtualrun-location.log
```

记录问题出现的准确时间，以及手机型号、Android 版本、跑步 App 名称/版本。采集完成后按 Ctrl+C。日志包含经纬度，请在公开分享前脱敏。

- `MockLocationPump`：开始/停止、目标周期、tick、GPS/Network 成功计数、Provider 初始化和恢复。
- `MockLocationPush`：各通道成功计数、实际发送/确认间隔、经纬度、速度、航向、精度、高程、卫星数、wall time、观测 elapsedRealtimeNanos、发送/确认 sendElapsedNanos。FLP 的成功是 Task 完成，不等于第三方消费成功。
- `MockGoogleFlp`：Google 服务可用状态、mock mode、完整异常、退避和在途超时。

## 判断断流位置

| 证据 | 可得出的结论 |
| --- | --- |
| tick 本身停止 | 检查进程、服务、线程、WakeLock 和系统后台限制 |
| tick 连续，某通道一直失败 | 检查该 Provider/权限/Google 服务；不能归因于轨迹算法 |
| 写入成功，独立接收端没有回调 | 检查系统定位分发和接收端请求条件 |
| 独立接收端连续，跑步 App 不记录 | 需要该 App/设备的进一步证据，可能涉及请求路径、精度要求或模拟位置过滤，不能仅凭发送日志断言原因 |

## 回归测试清单

- 单元测试：1/5/10Hz 距离与调速；重复/倒退时间戳；八点路线 5/10Hz 跨圈连续性；立即停止；开放路线终点；退避上限与复位。
- Android 15 模拟器：选八点闭环，先启动模拟，后启动独立高精度接收端，分别订阅 GPS、Network、platform fused、Google FLP。
- 运行中移除/禁用测试 Provider，检查恢复日志和接收回调重新出现。
- 锁屏与切后台时检查投递间隔；Google 服务不可用时检查 GPS/Network 持续计数。
- 停止后检查不再生成新观测，Provider 已清理；重复启停不残留旧服务投递。
- **仍需实际手机及发生问题的跑步软件验证**，模拟器和测试接收端不能替代该验收。

## 参考而非复制

参考了 [ZCShou/GoGoGo 的 ServiceGo.java](https://github.com/ZCShou/GoGoGo/blob/master/app/src/main/java/com/zcshou/service/ServiceGo.java) 的独立前台优先级定位线程及约 100ms GPS/Network 投递思路。该公开文件不包含本项目的 Google FLP Adapter 架构；本文所述通道隔离、退避、日志及轨迹时间接口是本项目的实现。
