#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ipc.h>
#include <sys/msg.h>
#include <sys/types.h>

#include "config.h"
#include "util.c"

int main(void) {
  struct msg_buff buf;
  char msg[200];
  buf.mtext = msg;
  int msqid = get_queue();

  buf.mtype = 1; /* we don't really care in this case */

  while (fgets(buf.mtext, sizeof buf.mtext, stdin) != NULL) {
    int len = strlen(buf.mtext);

    /* ditch newline at end, if it exists */
    if (buf.mtext[len - 1] == '\n') buf.mtext[len - 1] = '\0';

    if (send(msqid, &buf) == -1) die("msgsnd");
  }
}