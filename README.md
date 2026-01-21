<div align="center">
    <img alt="logo" width="106px" src="https://github.com/ichenhe/halo-lsky-pro/assets/10266066/9dc173b0-d95e-457d-ba33-9eb2ad3e1f93">
    <h1>Halo - Lsky Pro</h1>
    <p>集成 <a href="https://www.lsky.pro/">Lsky Pro</a> 兰空图床作为 <a href="https://www.halo.run/">Halo</a> 的存储后端。</p>
    <p align="center">
        <a href="https://www.halo.run/store/apps/app-jZHhX?tab=readme"><img alt="Halo App Store" src="https://img.shields.io/badge/Halo-%E5%BA%94%E7%94%A8%E5%B8%82%E5%9C%BA-%230A81F5?style=flat-square&logo=appstore&logoColor=%23fff" /></a>
        <a href="//github.com/ichenhe/halo-lsky-pro/releases"><img alt="GitHub Release" src="https://img.shields.io/github/v/release/ichenhe/halo-lsky-pro?style=flat-square&logo=github" /></a>
        <a href="//github.com/ichenhe/halo-lsky-pro/actions/workflows/ci.yaml"><img alt="GitHub Actions Workflow Status" src="https://img.shields.io/github/actions/workflow/status/ichenhe/halo-lsky-pro/ci.yaml?style=flat-square&label=build" /></a>
        <a href="./LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/ichenhe/halo-lsky-pro?style=flat-square" /></a>
    </p>

</div>

局限性：

- 仅支持 Lsky Pro v2，不支持旧版本。
- 由于 Lsky Pro 限制，仅支持上传图片类附件。（不影响上传任意文件到其他存储策略）
- 由于 Lsky Pro 限制，若启用图床端格式转换（图片压缩）将导致 Halo 中显示的附件大小不正确。

## 📖 使用说明

本插件无需设置，安装后请到 Halo 后台「附件 - 存储策略」处添加策略。

### API Token

Lsky Pro v2 后台没有生成 Token 功能，必须通过请求 API 接口获得。具体接口定义在 Lsky Pro 后台可以看到。

#### cURL 方式

若计算机已安装 curl 则可以通过命令行获取：

```bash
curl --location --request POST 'https://example.com/api/v1/tokens' \
--form 'email="your-email"' \
--form 'password="your-password"'
```

#### 在线 HTTP 请求工具

例如 [Getman](https://getman.cn/):

![](https://github.com/ichenhe/halo-lsky-pro/assets/10266066/94b54967-5198-4555-abf6-da9651a6bba1)


### 相册 ID

> [!NOTE]
>
> 开源版兰空图床不支持此参数，设置后无效。

此处可以指定上传图片的归属相册，留空则不指定（即不属于任何相册）。

如果 Lsky Pro 后台不显示相册 ID。打开浏览的开发者工具 (`F12`)，切换到网络 (Network) 标签页，然后再点击 「我的图片 - 相册 - 选中一个相册」，此时可以看到多了一个请求，名称形如 `images?page=1&album_id=1`，从这就可以得到相册 ID 啦。

当然也可以通过 API 获取，如果你有 curl 的话那么执行：

```bash
curl https://yourdomain.com/api/v1/albums -H 'Authorization: Bearer {your-api-token}'
```

### 实例 ID

Halo 的设计非常灵活，允许安装一个插件后基于不同参数（例如不同 Lsky Pro 服务器）创建多个存储策略，故本插件需要一种方式判断某个图片（附件）与哪一个 Lsky Pro 实例关联，从而正确删除图片。「实例 ID」就是做这个用的，具体来说：

- 每个附件都会在上传时记录当前的实例 ID，并且永远不会改变。
- 即使重新安装插件，或更改图床地址，或执行其他任何操作，只要实例 ID 与附件记录的匹配就会自动关联。

实例 ID 可以是任意字符串。更改实例 ID 将导致之前上传的附件失去关联。

> [!TIP]
>
> **推荐一开始就手动设置实例 ID。**
>
> 默认生成的 ID 与 Lsky Pro 地址关联（忽略协议）。这意味着地址更换将导致之前上传的附件失去关联，从 Halo 删除时无法同步删除 Lsky Pro 中的文件。


## 建议/反馈

这里是免费开源的第三方插件，无论是否为 Halo 商业用户都不会获得独特的售后服务，请前往仓库的 issues 进行反馈。

求助请描述清楚问题，尽量附上你的配置，错误日志，故障截图等。建议请写明背景和用例。

一句话的反馈将被无条件关闭。
