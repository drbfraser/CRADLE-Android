# Use a base image with JDK 17
FROM adoptopenjdk:17-jre-hotspot

# Set environment variables for Gradle
ENV GRADLE_VERSION 8.1.2

# Download and install Gradle
RUN apt-get update && \
    apt-get install -y wget && \
    wget https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip -P /tmp && \
    unzip -d /opt/gradle /tmp/gradle-${GRADLE_VERSION}-bin.zip && \
    rm /tmp/gradle-${GRADLE_VERSION}-bin.zip

# Set Gradle home and add it to the PATH
ENV GRADLE_HOME /opt/gradle/gradle-${GRADLE_VERSION}
ENV PATH $PATH:$GRADLE_HOME/bin

# Example: Copy the local Gradle project into the container
COPY . /app

# Specify the default command to run when the container starts
CMD ["gradle", "--version"]
