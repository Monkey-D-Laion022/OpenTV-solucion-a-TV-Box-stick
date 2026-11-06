#ifndef PS3_AV_H
#define PS3_AV_H
#include <stddef.h>
#include <stdint.h>
int  ps3av_init(void);
void ps3av_push_video(const uint8_t *annexb, size_t len, int64_t pts90k, int key);
void ps3av_push_audio(const uint8_t *adts, size_t len, int64_t pts90k);
void ps3av_message(const char *text);
#endif
