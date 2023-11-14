# Using image with Amazon Linux, JDK 17 pre installed
FROM amazoncorretto:17

RUN yum update -y

#Install git
RUN yum install git -y

# install dependencies
RUN yum install -y wget unzip && yum clean all

# Define the Gradle version you want to install
ENV GRADLE_VERSION 8.0

# Download and install Gradle
RUN wget -q --no-check-certificate https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip && \
    unzip gradle-${GRADLE_VERSION}-bin.zip -d /opt && \
    rm gradle-${GRADLE_VERSION}-bin.zip

# Add Gradle to PATH
ENV PATH $PATH:/opt/gradle-${GRADLE_VERSION}/bin
ENV SDK_HOME $PATH:/opt

ENV ANDROID_TARGET_SDK="android-34" \
    ANDROID_BUILD_TOOLS="34.0.0" \
    ANDROID_SDK_TOOLS="7583922"

ENV ANDROID_SDK_URL https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_SDK_TOOLS}_latest.zip

RUN curl -sSL "${ANDROID_SDK_URL}" -o android-sdk-linux.zip \
    && unzip android-sdk-linux.zip -d android-sdk-linux \
  && rm -rf android-sdk-linux.zip
  
# Set ANDROID_HOME
ENV ANDROID_HOME $PWD/android-sdk-linux
ENV PATH ${ANDROID_HOME}/bin:$PATH

# Update and install using sdkmanager 
RUN echo yes | $ANDROID_HOME/cmdline-tools/bin/sdkmanager --sdk_root=${ANDROID_HOME} --licenses
#RUN echo yes | $ANDROID_HOME/cmdline-tools/bin/sdkmanager --sdk_root=${ANDROID_HOME} --update
RUN echo yes | $ANDROID_HOME/cmdline-tools/bin/sdkmanager --sdk_root=${ANDROID_HOME} "tools" "platform-tools" "emulator"
RUN echo yes | $ANDROID_HOME/cmdline-tools/bin/sdkmanager --sdk_root=${ANDROID_HOME} "build-tools;${ANDROID_BUILD_TOOLS}"
RUN echo yes | $ANDROID_HOME/cmdline-tools/bin/sdkmanager --sdk_root=${ANDROID_HOME} "platforms;${ANDROID_TARGET_SDK}"
RUN echo yes | $ANDROID_HOME/cmdline-tools/bin/sdkmanager --sdk_root=${ANDROID_HOME} "extras;android;m2repository" "extras;google;google_play_services" "extras;google;m2repository"
RUN echo yes | $ANDROID_HOME/cmdline-tools/bin/sdkmanager --sdk_root=${ANDROID_HOME} "extras;m2repository;com;android;support;constraint;constraint-layout;1.0.2"
RUN echo yes | $ANDROID_HOME/cmdline-tools/bin/sdkmanager --sdk_root=${ANDROID_HOME} "extras;m2repository;com;android;support;constraint;constraint-layout-solver;1.0.2"

ENV PATH ${SDK_HOME}/bin:$PATH
