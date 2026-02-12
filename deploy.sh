#!/bin/bash

CUR_DIR=$(pwd)
cd ai-tools-engine
./gradlew :cli:run --args="--working-dir $CUR_DIR"
cd ..
