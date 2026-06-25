.section .rodata
.globl binary_bootstrap_zip_start
.globl binary_bootstrap_zip_end
.globl binary_bootstrap_zip_size

binary_bootstrap_zip_start:
    .incbin "bootstrap-aarch64.zip"
binary_bootstrap_zip_end:
    .align 4
binary_bootstrap_zip_size:
    .int binary_bootstrap_zip_end - binary_bootstrap_zip_start
