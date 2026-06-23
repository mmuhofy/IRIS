cmake_minimum_required(VERSION 3.22)
project(elfloader VERSION 1.0 LANGUAGES C)

add_library(elfloader SHARED elfloader.c)

target_compile_options(elfloader PRIVATE -std=c11 -Wall -Wextra -Werror -Os)
target_link_libraries(elfloader PRIVATE log)
