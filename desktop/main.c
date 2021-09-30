#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ipc.h>
#include <sys/msg.h>
#include <sys/types.h>

#include "qrcode.h"

struct msg_buff {
  long mtype;
  char mtext[200];
};

int request_connection(void) {
  struct msg_buff buf;
  int msqid;
  key_t key;

  if ((key = ftok("/home/me/pja/thesis/extended_desktop/deamon", 'A')) == -1) {
    perror("ftok");
    exit(1);
  }

  if ((msqid = msgget(key, 0644 | IPC_CREAT)) == -1) {
    perror("msgget");
    exit(1);
  }

  buf.mtype = 1; /* we don't really care in this case */

  while (fgets(buf.mtext, sizeof buf.mtext, stdin) != NULL) {
    int len = strlen(buf.mtext);

    /* ditch newline at end, if it exists */
    if (buf.mtext[len - 1] == '\n') buf.mtext[len - 1] = '\0';

    if (msgsnd(msqid, &buf, len + 1, 0) == -1) /* +1 for '\0' */
      perror("msgsnd");
  }

  return 0;
}

int main(void) {
  QRCode qrcode;
  uint8_t qrcodeData[qrcode_getBufferSize(3)];
  qrcode_initText(&qrcode, qrcodeData, 3, 0, "HELLO WORLD");

  // Top quiet zone
  printf("\n\n\n\n");

  for (uint8_t y = 0; y < qrcode.size; y++) {
    // Left quiet zone
    printf("        ");

    // Each horizontal module
    for (uint8_t x = 0; x < qrcode.size; x++) {
      // Print each module (UTF-8 \u2588 is a solid block)
      printf(qrcode_getModule(&qrcode, x, y) ? "\u2588\u2588" : "  ");
    }

    printf("\n");
  }

  // Bottom quiet zone
  printf("\n\n\n\n");
}