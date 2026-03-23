# Contributing to Binti

شكراً لاهتمامك بالمساهمة في بنتي! 🎉

Thank you for your interest in contributing to Binti!

## 🌍 Languages

This document is available in:
- [Arabic (العربية)](#العربية)
- [English](#english)

---

## العربية

### كيف تساهم

هناك طرق كتير لمساهمتك في بنتي:

#### 1. الإبلاغ عن المشاكل 🐛
لو لقيت bug أو مشكلة:
1. افتح [Issue جديد](https://github.com/waleedmandour/binti2/issues/new)
2. وصف المشكلة بالتفصيل
3. اكتب خطوات إعادة إنتاج المشكلة
4. أرفع صور أو فيديو لو ممكن

#### 2. اقتراح ميزات 💡
لو عندك فكرة لميزة جديدة:
1. افتح [Issue جديد](https://github.com/waleedmandour/binti2/issues/new)
2. استخدم label "enhancement"
3. شرح الفكرة وازاي هتساعد المستخدمين

#### 3. تحسين الترجمات 🌐
- أضف عبارات مصرية جديدة
- صحح ترجمات موجودة
- أضف لهجات إقليمية (إسكندرانية، صعيدية، إلخ)

#### 4. كتابة الكود 💻
شوف [Development Setup](#development-setup)

### إرشادات الكود

#### Kotlin Style
```kotlin
// استخدم أسماء واضحة
fun processVoiceCommand(audioData: ShortArray): String {
    // Implementation
}

// اكتب تعليقات للدوال المهمة
/**
 * يعالج الأمر الصوتي ويرجع النص
 * @param audioData البيانات الصوتية
 * @return النص المستخرج
 */
```

#### Arabic String Resources
```xml
<!-- استخدم العربية المصرية العادية -->
<string name="welcome">أهلاً وسهلاً يا حبيبي!</string>

<!-- تجنب الفصحى المعقدة -->
<!-- ❌ -->
<string name="welcome">نرحب بكم في تطبيقنا</string>
```

#### Commit Messages
```
# بالعربي أو الإنجليزي
feat: إضافة أمر تكييف جديد
fix: إصلاح مشكلة الاستماع في الخلفية
docs: تحديث التوثيق
```

---

## English

### How to Contribute

There are many ways to contribute to Binti:

#### 1. Report Bugs 🐛
If you find a bug:
1. Open a [new Issue](https://github.com/waleedmandour/binti2/issues/new)
2. Describe the problem in detail
3. Include steps to reproduce
4. Add screenshots or videos if possible

#### 2. Suggest Features 💡
Have a new feature idea?
1. Open a [new Issue](https://github.com/waleedmandour/binti2/issues/new)
2. Use the "enhancement" label
3. Explain the idea and how it helps users

#### 3. Improve Translations 🌐
- Add new Egyptian Arabic phrases
- Correct existing translations
- Add regional dialects (Alexandrian, Upper Egyptian, etc.)

#### 4. Write Code 💻
See [Development Setup](#development-setup)

### Code Guidelines

#### Kotlin Style
```kotlin
// Use clear, descriptive names
fun processVoiceCommand(audioData: ShortArray): String {
    // Implementation
}

// Document important functions
/**
 * Processes voice command and returns transcribed text
 * @param audioData Audio samples
 * @return Transcribed text
 */
```

#### Arabic String Resources
```xml
<!-- Use natural Egyptian Arabic -->
<string name="welcome">أهلاً وسهلاً يا حبيبي!</string>

<!-- Avoid formal MSA -->
<!-- ❌ -->
<string name="welcome">نرحب بكم في تطبيقنا</string>
```

#### Commit Messages
```
feat: add new climate command
fix: fix background listening issue
docs: update documentation
```

---

## Development Setup

### Prerequisites
- Android Studio Hedgehog or later
- JDK 17+
- Android SDK 34
- NDK 25+ (for native code)

### Getting Started

```bash
# Fork and clone
git clone https://github.com/YOUR_USERNAME/binti2.git
cd binti2

# Create feature branch
git checkout -b feature/my-feature

# Build project
./gradlew assembleDebug

# Run tests
./gradlew test
```

### Project Structure
```
app/src/main/java/com/binti/dilink/
├── voice/           # Voice processing
├── nlp/             # NLU components
├── dilink/          # DiLink integration
├── response/        # TTS and responses
├── overlay/         # UI overlay
└── utils/           # Utilities
```

### Testing

#### Unit Tests
```bash
./gradlew test
```

#### Instrumented Tests
```bash
./gradlew connectedAndroidTest
```

### Pull Request Process

1. **Fork** the repository
2. **Create** a feature branch
3. **Make** your changes
4. **Test** thoroughly
5. **Submit** a pull request

### PR Requirements
- [ ] Code compiles without errors
- [ ] Tests pass
- [ ] Code follows style guidelines
- [ ] Documentation updated (if needed)

---

## Community Guidelines

### Code of Conduct
- Be respectful and inclusive
- Welcome newcomers
- Accept constructive criticism
- Focus on what's best for the community

### Getting Help
- 💬 [Discord](https://discord.gg/binti)
- 📱 [Telegram](https://t.me/BintiAssistant)
- 📧 Email: support@binti.app

---

## Recognition

Contributors will be recognized in:
- README.md contributors section
- Release notes
- App credits screen

---

<p align="center">
  شكراً لمساهمتك! 🙏<br>
  Thank you for contributing!
</p>
