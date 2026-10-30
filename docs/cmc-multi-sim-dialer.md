# CMC 跨设备多 SIM 卡拨号架构与修复方案（2026-10-30）

## 背景与需求

- **设备角色定位**：
  - **S24U（主力机）**：始终作为 **副设备（SD，Secondary Device，`mdecDeviceType = 2`）**，随身携带，需要拨打和使用所有 SIM 卡。
  - **S21+（备用机）**：始终作为 **主设备（PD，Primary Device，`mdecDeviceType = 1`）**，放宿舍，提供远程 SIM 卡能力。
- **SIM 卡分配可能**：
  - S24U 插 2 张卡，S21+ 插 1 张卡（共 3 卡）
  - S24U 插 1 张卡，S21+ 插 2 张卡（共 3 卡）
  - S24U 插 2 张卡，S21+ 插 2 张卡（共 4 卡）
- **核心诉求**：S24U 必须能够无缝切换并使用所有 SIM 卡进行外拨与短信通信。

---

## 根因分析：拨号盘双按键与药丸切换栏冲突

### 1. 三星拨号盘布局判定机制（`DialpadButtonHelper` / `DialpadButtonHelperCHN`）

在反编译的 `SamsungDialer.apk` 中：
```java
// sources/h8/d.java (DialpadButtonHelperCHN)
@Override
public final int n() { // getLayoutType
    int i = 0;
    if (((Hg.a) this.f24050a.v()).f()) {
        return 0; // 卫星通信模式
    }
    if (this.f24053d && this.e) { // isMultiSim && isManualSimSelectMode
        i = 21; // 国行双卡拨号盘布局
    }
    return i;
}
```

当 S24U 插入双卡后：
- `f24053d`（`isMultiSim`）= `true`
- `e`（`isManualSimSelectMode`）= `true`
- `getLayoutType()` 返回 **`21`**。

### 2. 布局 21 的缺陷：硬编码双键，完全无视 CMC

在布局 `21` 下，拨号盘界面生成器 `DialpadButtonGenerator`（`r8/C2028a.java`）实例化 `V`，在中央并排渲染两个按钮：
- `dialButton1`：`[ ✆ 1 (SIM 1 拨号) ]`
- `dialButton2`：`[ ✆ 2 (SIM 2 拨号) ]`

查看点击处理函数（`H5/f.java`）：
```java
case R.id.dialButton1:
    // 硬编码呼叫本地 SIM 1
    eVar.e(..., isSim0 ? 0 : 1);
    break;
case R.id.dialButton2:
    // 硬编码呼叫本地 SIM 2
    eVar.f(..., isSim1 ? 12 : 13);
    break;
```
**两个按钮只呼叫本地卡 1 和本地卡 2，完全忽略了下方通过 CMC 钩子呼出的 `DialPadBottomToggleCmc` 药丸切换栏！**
因此，用户虽然在下方看到了药丸栏，但上面的两个拨号按钮根本不理会它，**第三张（S21+ 远程）SIM 卡彻底无法通过拨号键呼出**。

---

## 三星原生 CMC 多卡架构与 4 卡支持能力

### 1. 布局 0（单拨号键 + 底部药丸切换栏）

当 `getLayoutType()` 返回 **`0`** 时：
1. 界面呈现为 **1 个居中通话按钮 `[ ✆ ]`**，下方为 **卡槽切换药丸栏 `[ ⬚ ... ]`**。
2. 药丸栏（`f0/k.java:d()`）会列出所有 `callCapablePhoneAccounts`：
   - 本地 SIM 1（slotId 0）→ 显示为 `中国移动 (SIM 1)`，返回 `accountHandle = 0`
   - 本地 SIM 2（slotId 1）→ 显示为 `中国广电 (SIM 2)`，返回 `accountHandle = 1`
   - 远程 CMC SIM 1（`CMC_0`）→ 显示为 `远程设备 SIM 1`，返回 `accountHandle = 3`
   - 远程 CMC SIM 2（`CMC_1`）→ 显示为 `远程设备 SIM 2`，返回 `accountHandle = 4`
3. 用户点击药丸栏时，`j()` 自动循环切换选中的账户。
4. 用户点击居中呼叫按钮时（`H5/f.java` 行 215）：
   ```java
   int iC = c1298c2.x.c(); // 读取药丸栏当前选中的 accountHandle
   eVar.d(oVar3, new V7.a(str2, 131068), iC); // 调用 placeCall
   ```
   进而调用 `DialpadButtonHelper.z(oVar, aVar, i)`：
   ```java
   if (i == 0) {
       oVar.D1(12, aVar); // 本地 SIM 1
   } else if (i == 1) {
       oVar.D1(13, aVar); // 本地 SIM 2
   } else {
       oVar.D1(10, aVar); // CMC 呼叫！
   }
   ```
5. `Oc/b.java:h()` 接收到 `accountHandle`：
   - `0` / `1` → 注入对应本地卡槽的 `PHONE_ACCOUNT_HANDLE`
   - `3` → 注入 `CMC_0` 的 `PHONE_ACCOUNT_HANDLE`
   - `4` → 注入 `CMC_1` 的 `PHONE_ACCOUNT_HANDLE`

### 2. 是否支持 4 张 SIM 卡？

**支持！原生上限正好是 4 张 SIM 卡（2 本地 + 2 远程）**：
- S24U 插双卡：本地卡 1（`0`）、本地卡 2（`1`）
- S21+ 插双卡：远程卡 1（`CMC_0`，代码 `3`）、远程卡 2（`CMC_1`，代码 `4`）
- 在单按钮布局下，药丸切换栏会在 **4 张 SIM 卡** 之间顺序循环切换，且每次切换均能在居中按钮一键拨出。

---

## 修复实施细节

1. **`Call.kt` 拦截 `getLayoutType`**：
   - 当 `mdecDeviceType == 2`（副设备 SD 模式）时，使用 DexKit 拦截 `DialpadButtonHelper` 与 `DialpadButtonHelperCHN` 的 `getLayoutType` 方法；
   - 强制返回 `0`（单拨号按钮）；
   - 在拦截器内将 `DialpadButtonHelper` 实例的 `f24053d`（`isMultiSim`）和 `e`（`isManualMode`）重置为 `false`，促使 `a()` 进入 `bindCenterButton` 分支，正确设置居中通话按钮的图标与文字。
2. **S21+ 网络配置修复**：
   - 将主设备 S21+ 上 `/data/adb/lspd/modules/0/io.github.soclear/oneuix/files/preference.json` 中的 `"virtualLanPeerIp":"o10.0.0.130"` 纠正为 `"10.0.0.130"`。
