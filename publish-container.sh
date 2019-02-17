#!/bin/bash -e

PREFIX=

N=${1?}

IMAGE_NAME=${PWD##*/}_${N?}

DOCKER_REPO=msapatternsmultirepo

FN=${DOCKER_REPO}/${N?}:latest

docker login -u ${DOCKER_USER_ID?} -p ${DOCKER_PASSWORD?}

docker-compose build ${N}

$PREFIX docker tag ${IMAGE_NAME} ${FN}

$PREFIX docker push ${FN}
