cmake_minimum_required(VERSION 3.10)
project("mobileglues_info_getter")

add_library(mobileglues_info_getter SHARED mobileglues_info_getter.cpp)

find_library(log-lib log)
find_library(android-lib android)

set(PLATFORM_LIBS ${log-lib} dl ${android-lib})

target_include_directories(mobileglues_info_getter PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
target_link_libraries(mobileglues_info_getter ${PLATFORM_LIBS})
