#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ipc.h>
#include <sys/msg.h>
#include <sys/types.h>

struct msg_buff {
  long mtype;
  char mtext[200];
};

int main(void) {
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

  printf("extd: ready to receive messages.\n");

  for (;;) {
    if (msgrcv(msqid, &buf, sizeof buf.mtext, 0, 0) == -1) {
      perror("msgrcv");
      exit(1);
    }

    if (strcmp(buf.mtext, "quit") == 0) break;

    printf("extd: \"%s\"\n", buf.mtext);
  }

  if (msgctl(msqid, IPC_RMID, NULL) == -1) {
    perror("msgctl");
    exit(1);
  }

  return 0;
}