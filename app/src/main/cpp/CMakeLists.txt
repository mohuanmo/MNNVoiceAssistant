cmake_minimum_required(VERSION 3.22.1)
project("mnnvoiceassistant")

set(CMAKE_CXX_STANDARD 17)
find_library(log-lib log)

add_library(
    mnn-jni
    SHARED
    native-lib.cpp
)

target_link_libraries(
    mnn-jni
    ${log-lib}
)
