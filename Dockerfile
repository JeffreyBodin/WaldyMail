# syntax=docker/dockerfile:1
# Postbox Offline Mail - Containerized Android APK/AAB Builder & Artifact Package
# Zero server, zero exposed ports, zero hosted runtime. Produces offline Android binaries and exits.

FROM eclipse-temurin:17-jdk-jammy AS builder

ARG GRADLE_VERSION=9.3.1
ARG CMDLINE_TOOLS_VERSION=11076708
ARG GRADLE_TASK=:app:assembleDebug

ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV ANDROID_HOME=/opt/android-sdk
ENV PATH="${PATH}:/opt/gradle/bin:${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:${ANDROID_SDK_ROOT}/platform-tools"

RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    unzip \
    ca-certificates \
  && rm -rf /var/lib/apt/lists/*

# Install Gradle 9.3.1
RUN curl -fsSL "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" -o /tmp/gradle.zip \
  && unzip -q /tmp/gradle.zip -d /opt \
  && mv "/opt/gradle-${GRADLE_VERSION}" /opt/gradle \
  && rm -f /tmp/gradle.zip

# Install Android SDK Command-Line Tools & SDK 36
RUN mkdir -p "${ANDROID_SDK_ROOT}/cmdline-tools" \
  && curl -fsSL "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip" -o /tmp/cmdline-tools.zip \
  && unzip -q /tmp/cmdline-tools.zip -d "${ANDROID_SDK_ROOT}/cmdline-tools" \
  && mv "${ANDROID_SDK_ROOT}/cmdline-tools/cmdline-tools" "${ANDROID_SDK_ROOT}/cmdline-tools/latest" \
  && rm -f /tmp/cmdline-tools.zip \
  && yes | sdkmanager --licenses > /dev/null \
  && sdkmanager --install "platform-tools" "platforms;android-36" "build-tools;36.0.0"

WORKDIR /workspace
COPY . .

# Generate debug.keystore from base64 template if present and build offline APK/AAB
RUN if [ -f debug.keystore.base64 ] && [ ! -f debug.keystore ]; then \
      base64 -d debug.keystore.base64 > debug.keystore; \
    fi \
  && gradle --no-daemon "${GRADLE_TASK}" \
  && mkdir -p /artifacts \
  && find app/build/outputs -type f \( -name "*.apk" -o -name "*.aab" \) -exec cp {} /artifacts/ \;

# Target 1: Direct BuildKit artifact export (`docker build --target artifact --output ./dist .`)
FROM scratch AS artifact
COPY --from=builder /artifacts/ /

# Target 2: Portable offline exporter container (runs once, exports binaries, exits)
FROM alpine:3.20 AS exporter
WORKDIR /artifacts
COPY --from=builder /artifacts/ /artifacts/
ENTRYPOINT ["/bin/sh", "-c", "ls -lh /artifacts && sha256sum /artifacts/* && if [ -d /export ]; then cp -v /artifacts/* /export/; fi"]
