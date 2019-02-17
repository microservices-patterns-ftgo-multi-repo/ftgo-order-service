#!/bin/bash -e

PREFIX=

N=${PWD##*/}
IMAGE_NAME=${N?}_${N?}

DOCKER_REPO=msapatternsmultirepo

FN=${DOCKER_REPO}/${IMAGE_NAME?}:latest

docker login -u ${DOCKER_USER_ID?} -p ${DOCKER_PASSWORD?}

docker-compose build ${N}

$PREFIX docker tag ${IMAGE_NAME} ${FN}

$PREFIX docker push ${FN}
