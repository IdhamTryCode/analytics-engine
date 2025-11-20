#!/usr/bin/env bash
# Bash script to build JAR file for java-engine service
# This must be run before building the docker image
# Equivalent to build-jar.ps1 for Linux/macOS

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
WHITE='\033[1;37m'
NC='\033[0m' # No Color

# Get script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LEGACY_DIR="$SCRIPT_DIR/analytics-core-legacy"

# Try to get JAVA_HOME from environment, otherwise use common Linux locations
if [ -z "${JAVA_HOME:-}" ]; then
    # Try common Java installation locations
    if [ -d "/usr/lib/jvm/java-21-openjdk-amd64" ]; then
        JAVA_HOME="/usr/lib/jvm/java-21-openjdk-amd64"
    elif [ -d "/usr/lib/jvm/java-21" ]; then
        JAVA_HOME="/usr/lib/jvm/java-21"
    elif [ -d "/opt/java/jdk-21" ]; then
        JAVA_HOME="/opt/java/jdk-21"
    elif [ -d "$HOME/.sdkman/candidates/java/current" ]; then
        JAVA_HOME="$HOME/.sdkman/candidates/java/current"
    else
        JAVA_HOME=""
    fi
fi

echo -e "${CYAN}Building JAR file for analytics-engine...${NC}"

# Check if analytics-core-legacy directory exists
if [ ! -d "$LEGACY_DIR" ]; then
    echo -e "${RED}Error: analytics-core-legacy directory not found at: $LEGACY_DIR${NC}"
    exit 1
fi

cd "$LEGACY_DIR"

# Check if Java is installed
if ! command -v java >/dev/null 2>&1; then
    echo -e "${RED}Error: Java is not installed or not in PATH${NC}"
    echo ""
    
    # Try to use Java from JAVA_HOME if set
    if [ -n "$JAVA_HOME" ] && [ -f "$JAVA_HOME/bin/java" ]; then
        echo -e "${YELLOW}Trying to use Java from: $JAVA_HOME/bin${NC}"
        export PATH="$JAVA_HOME/bin:$PATH"
        
        if ! command -v java >/dev/null 2>&1; then
            echo -e "${RED}Error: Java still not found after adding JAVA_HOME to PATH${NC}"
            echo -e "${YELLOW}Please install Java 21+ or set JAVA_HOME environment variable${NC}"
            exit 1
        fi
    else
        echo -e "${YELLOW}Please install Java 21+ or set JAVA_HOME environment variable${NC}"
        exit 1
    fi
fi

# Check Java version
JAVA_VERSION_LINE=$(java -version 2>&1 | head -n 1)
if [[ "$JAVA_VERSION_LINE" =~ version\ \"([0-9]+) ]]; then
    JAVA_MAJOR="${BASH_REMATCH[1]}"
    if [ "$JAVA_MAJOR" -lt 21 ]; then
        echo -e "${RED}Error: Java 21+ is required, but found Java $JAVA_MAJOR${NC}"
        exit 1
    fi
    echo -e "${GREEN}Java version: $JAVA_MAJOR${NC}"
else
    echo -e "${YELLOW}Warning: Could not determine Java version, continuing anyway...${NC}"
fi

# Set JAVA_HOME if not already set and we found Java
if [ -z "${JAVA_HOME:-}" ]; then
    JAVA_PATH=$(which java)
    if [ -n "$JAVA_PATH" ]; then
        # Resolve symlinks to find actual Java installation
        JAVA_REAL=$(readlink -f "$JAVA_PATH" 2>/dev/null || echo "$JAVA_PATH")
        JAVA_HOME=$(dirname "$(dirname "$JAVA_REAL")")
        export JAVA_HOME
    fi
fi

# Get version from pom.xml
if [ ! -f "pom.xml" ]; then
    echo -e "${RED}Error: pom.xml not found in $LEGACY_DIR${NC}"
    exit 1
fi

VERSION=$(grep -m1 -oP '(?<=<version>)[^<]+' pom.xml || echo "unknown")
echo -e "${CYAN}Building version: $VERSION${NC}"

# Build JAR file
echo -e "${CYAN}Running Maven build (this may take a while)...${NC}"

# Export JAVA_HOME for Maven
export JAVA_HOME

# Try mvnw (Maven wrapper for Linux/macOS) first, then run Maven wrapper jar directly
if [ -x "./mvnw" ]; then
    echo -e "${CYAN}Using Maven wrapper executable...${NC}"
    ./mvnw clean install -DskipTests -Dcheckstyle.skip=true -Dair.check.skip-dependency=true -P exec-jar
elif [ -f "./.mvn/wrapper/maven-wrapper.jar" ]; then
    # Run Maven wrapper jar directly with Java
    MVNW_JAR=$(realpath "./.mvn/wrapper/maven-wrapper.jar")
    PROJECT_BASE_DIR=$(pwd)
    
    echo -e "${CYAN}Using Maven wrapper jar directly...${NC}"
    # First, build all modules without exec-jar profile (to avoid profile validation error)
    echo -e "${YELLOW}Building all modules (with checkstyle and dependency analysis skipped)...${NC}"
    java \
        "-Dmaven.multiModuleProjectDirectory=$PROJECT_BASE_DIR" \
        "-classpath" "$MVNW_JAR" \
        org.apache.maven.wrapper.MavenWrapperMain \
        clean install \
        -DskipTests \
        -Dcheckstyle.skip=true \
        -Dmaven.checkstyle.skip=true \
        -Dair.check.skip-checkstyle=true \
        -Dair.check.skip-dependency=true
    
    if [ $? -ne 0 ]; then
        echo -e "${RED}Build failed. Check the error messages above.${NC}"
        exit 1
    fi
    
    echo -e "${GREEN}All modules built successfully. Building executable JAR for analytics-server...${NC}"
    # Now build analytics-server with exec-jar profile to create executable JAR
    java \
        "-Dmaven.multiModuleProjectDirectory=$PROJECT_BASE_DIR" \
        "-classpath" "$MVNW_JAR" \
        org.apache.maven.wrapper.MavenWrapperMain \
        package \
        -DskipTests \
        -Dcheckstyle.skip=true \
        -Dmaven.checkstyle.skip=true \
        -Dair.check.skip-checkstyle=true \
        -Dair.check.skip-dependency=true \
        -pl analytics-server \
        -P exec-jar
else
    echo -e "${RED}Error: Maven wrapper not found!${NC}"
    echo -e "${YELLOW}Please ensure you're in the analytics-core-legacy directory${NC}"
    exit 1
fi

if [ $? -eq 0 ]; then
    JAR_PATH="analytics-server/target/analytics-server-${VERSION}-executable.jar"
    
    if [ -f "$JAR_PATH" ]; then
        echo ""
        echo -e "${GREEN}JAR file built successfully!${NC}"
        echo -e "${CYAN}Location: $JAR_PATH${NC}"
        echo ""
        echo -e "${YELLOW}You can now build and run docker-compose:${NC}"
        echo -e "${WHITE}  docker compose build${NC}"
        echo -e "${WHITE}  docker compose up${NC}"
    else
        echo ""
        echo -e "${YELLOW}Build completed, but expected JAR was not found at: $JAR_PATH${NC}"
        echo -e "${YELLOW}Please verify the Maven output for details.${NC}"
    fi
else
    echo -e "${RED}Build failed!${NC}"
    exit 1
fi

