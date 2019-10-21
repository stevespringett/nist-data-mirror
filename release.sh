#!/usr/bin/env bash
export JAVA_HOME=$(/usr/libexec/java_home -v 1.8)
export PATH=JAVA_HOME/bin:$PATH

read -p "Really deploy to Maven Central repository (Y/N)? "
if ( [ "$REPLY" == "Y" ] ) then

  # Retrieves the current version from the pom. This will likely be in the format: x.x.x-SNAPSHOT
  CURRENT_VERSION=$(grep "^    <version>.*</version>$" pom.xml | awk -F'[><]' '{print $3}')
  # Define and remove the -SNAPSHOT suffix from CURRENT_VERSION and assign the result to RELEASE_VERSION
  suffix="-SNAPSHOT";
  RELEASE_VERSION=${CURRENT_VERSION%$suffix};
  # Increment RELEASE_VERSION by one. This should result in: x.x.x -> x.x.x+1
  NEXT_VERSION=$(echo $RELEASE_VERSION | awk -F. -v OFS=. 'NF==1{print ++$NF}; NF>1{if(length($NF+1)>length($NF))$(NF-1)++; $NF=sprintf("%0*d", length($NF), ($NF+1)%(10^length($NF))); print}')
  # Defines the next SNAPSHOT release version
  NEXT_SNAPSHOT_VERSION=$NEXT_VERSION-SNAPSHOT

  mvn clean
  mvn release:clean release:prepare release:perform -Prelease -X -e | tee release.log

else
  echo -e "Exit without deploy"
fi

# Cleanup containers/images, build new image and push to Docker Hub
REPO=sspringett/nvdmirror
docker rm nvdmirror
docker rmi $REPO:latest
docker rmi $REPO:$RELEASE_VERSION
docker build -f Dockerfile --no-cache=true --build-arg BUILD_DATE=$(date -u +'%Y-%m-%dT%H:%M:%SZ') -t $REPO:$RELEASE_VERSION -t $REPO:latest .
docker login
docker push $REPO:latest
docker push $REPO:$RELEASE_VERSION