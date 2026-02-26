#!/bin/bash

MVN_OPTS="-Duser.home=/var/maven -T 4"

if [ ! -e node_modules ]
then
  mkdir node_modules
fi

case `uname -s` in
  MINGW* | Darwin*)
    USER_UID=1000
    GROUP_GID=1000
    ;;
  *)
    if [ -z ${USER_UID:+x} ]
    then
      USER_UID=`id -u`
      GROUP_GID=`id -g`
    fi
esac

# options
SPRINGBOARD="recette"
for i in "$@"
do
case $i in
    -s=*|--springboard=*)
    SPRINGBOARD="${i#*=}"
    shift
    ;;
    *)
    ;;
esac
done

init() {
  me=`id -u`:`id -g`
  echo "DEFAULT_DOCKER_USER=$me" > .env

    # If CLI_VERSION is empty set $cli_version to latest
  	if [ -z "$CLI_VERSION" ]; then
  		CLI_VERSION="latest"
  	fi
  	# Create a build.compose.yaml file from following template
  	cat <<EOF > build.compose.yaml
services:
  edifice-cli:
    image: opendigitaleducation/edifice-cli:$CLI_VERSION
    user: "$DEFAULT_DOCKER_USER"
EOF
  	# Copy /root/edifice from edifice-cli container to host machine
  	docker compose -f build.compose.yaml create edifice-cli
  	docker compose -f build.compose.yaml cp edifice-cli:/root/edifice ./edifice
  	docker compose -f build.compose.yaml rm -fsv edifice-cli
  	rm -f build.compose.yaml
  	chmod +x edifice
  	./edifice version $EDIFICE_CLI_DEBUG_OPTION
}

test () {
  docker compose run --rm maven mvn $MVN_OPTS test
}

clean () {
  docker compose run --rm maven mvn $MVN_OPTS clean
}

buildNode () {
  #jenkins
  echo "[buildNode] Get branch name from jenkins env..."
  BRANCH_NAME=`echo $GIT_BRANCH | sed -e "s|origin/||g"`
  if [ "$BRANCH_NAME" = "" ]; then
    echo "[buildNode] Get branch name from git..."
    BRANCH_NAME=`git branch | sed -n -e "s/^\* \(.*\)/\1/p"`
  fi
  if [ ! -z "$FRONT_TAG" ]; then
    echo "[buildNode] Get tag name from jenkins param... $FRONT_TAG"
    BRANCH_NAME="$FRONT_TAG"
  fi
  if [ "$BRANCH_NAME" = "" ]; then
    echo "[buildNode] Branch name should not be empty!"
    exit -1
  fi

      echo "[buildNode] Use entcore version from package.json ($BRANCH_NAME)"
      case `uname -s` in
        MINGW*)
          docker compose run --rm -u "$USER_UID:$GROUP_GID" node sh -c "npm install --no-bin-links && npm update entcore && node_modules/gulp/bin/gulp.js build"
          ;;
        *)
          docker compose run --rm -u "$USER_UID:$GROUP_GID" node sh -c "npm install && npm update entcore && node_modules/gulp/bin/gulp.js build"
      esac
}

buildGradle () {
  docker compose run --rm maven mvn $MVN_OPTS install -DskipTests -U
}

publish () {
  version=`docker compose run --rm maven mvn $MVN_OPTS help:evaluate -Dexpression=project.version -q -DforceStdout`
  level=`echo $version | cut -d'-' -f3`
  case "$level" in
    *SNAPSHOT) export nexusRepository='snapshots' ;;
    *)         export nexusRepository='releases' ;;
  esac

  docker compose run --rm  maven mvn $MVN_OPTS -DrepositoryId=ode-$nexusRepository -DskipTests --settings /var/maven/.m2/settings.xml deploy
}

watch () {
  docker compose run --rm -u "$USER_UID:$GROUP_GID" node sh -c "node_modules/gulp/bin/gulp.js watch --springboard=/home/node/$SPRINGBOARD"
}

image() {
  ./edifice image --project-type=entcore $EDIFICE_CLI_DEBUG_OPTION --rebuild=false
}

for param in "$@"
do
  case $param in
    init)
      init
      ;;
    clean)
      clean
      ;;
    buildNode)
      buildNode
      ;;
    buildGradle)
      buildGradle
      ;;
    install)
      buildNode && buildGradle
      ;;
    watch)
      watch
      ;;
    test)
      test
      ;;
    publish)
      publish
      ;;
    image)
      image
      ;;
    *)
      echo "Invalid argument : $param"
  esac
  if [ ! $? -eq 0 ]; then
    exit 1
  fi
done

