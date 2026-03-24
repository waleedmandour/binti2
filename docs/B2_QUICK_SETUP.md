# Binti2 B2快速设置指南

## 📋 前提条件

您已提供Application Key: `00611ca67546bcae218a095413c0eb4bb951f57942`

但Backblaze B2需要**两个**值进行认证:
1. **Key ID** - 类似 `0012345678901234567890123` (以00开头)
2. **Application Key** - 40字符的密钥 (您已提供)

## 🔑 获取Key ID

1. 登录 [Backblaze B2](https://secure.backblaze.com/b2_buckets.htm)
2. 点击左侧 **App Keys**
3. 找到您创建的密钥
4. 复制 **keyID** 值

## ⚡ 快速设置

### 方法1: 使用配置文件

```bash
# 编辑配置文件
nano b2_config.env

# 填入您的keyID:
# B2_KEY_ID=00你的keyID
# B2_APP_KEY=00611ca67546bcae218a095413c0eb4bb951f57942

# 运行上传脚本
source b2_config.env && export B2_KEY_ID B2_APP_KEY
python scripts/upload_to_b2.py
```

### 方法2: 命令行参数

```bash
python scripts/upload_to_b2.py \
  --key-id "00你的keyID" \
  --app-key "00611ca67546bcae218a095413c0eb4bb951f57942"
```

### 方法3: 环境变量

```bash
export B2_KEY_ID="00你的keyID"
export B2_APP_KEY="00611ca67546bcae218a095413c0eb4bb951f57942"
python scripts/upload_to_b2.py
```

## 📦 上传模型文件

模型文件需要单独准备:

| 模型 | 文件 | 大小 | 来源 |
|------|------|------|------|
| 唤醒词 | `ya_binti_detector.tflite` | ~5MB | 需训练 |
| ASR | `vosk-model-ar-mgb2.zip` | ~1.2GB | [下载](https://alphacephei.com/vosk/models/vosk-model-ar-mgb2-0.4.zip) |
| NLU | `egybert_tiny_int8.onnx` | ~25MB | 需训练 |
| TTS | `ar-eg-female.zip` | ~80MB | 需训练 |
| 意图映射 | `dilink_intent_map.json` | ~10KB | 已有 |

### 下载Vosk阿拉伯语模型

```bash
cd models_to_upload
wget https://alphacephei.com/vosk/models/vosk-model-ar-mgb2-0.4.zip
mv vosk-model-ar-mgb2-0.4.zip vosk-model-ar-mgb2.zip
```

## ✅ 验证上传

上传完成后，验证文件可访问:

```bash
# 检查manifest
curl -I https://f001.backblazeb2.com/file/binti2-models/manifest.json

# 下载并查看
curl https://f001.backblazeb2.com/file/binti2-models/manifest.json | jq
```

## 🔄 GitHub Actions集成

在GitHub仓库设置Secrets:

1. 进入仓库 Settings → Secrets and variables → Actions
2. 添加:
   - `B2_KEY_ID` - 您的Key ID
   - `B2_APP_KEY` - 您的Application Key

CI工作流将自动在发布时上传模型。

## 🚀 离线优先架构

模型下载后的存储位置:
```
/data/data/com.binti.dilink/files/
├── models/
│   ├── wake/ya_binti_detector.tflite
│   ├── vosk-model-ar-mgb2/
│   └── nlu/egybert_tiny_int8.onnx
├── voices/
│   └── ar-eg-female/
└── assets/commands/dilink_intent_map.json
```

应用启动流程:
1. 检查本地模型状态
2. 如缺失，提示用户下载
3. 下载后完全离线运行

## 💰 成本估算

- 存储: 1.4GB × $0.005/GB = $0.007/月
- 下载: 10用户 × 1.4GB × $0.01/GB = $0.14/月
- **总计: ~$0.15/月**

免费层: 10GB存储 + 1GB/天下载
