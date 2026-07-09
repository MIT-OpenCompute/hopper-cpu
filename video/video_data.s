.section .rodata
.align 4
.global video_data
video_data:
.incbin "video.bin"
.global video_data_end
video_data_end: