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
  char msg[200];

  while (fgets(msg, sizeof msg, stdin) != NULL) {
    int len = strlen(msg);

    /* ditch newline at end, if it exists */
    // if (msg[len - 1] == '\n') msg[len - 1] = '\0';

    int size = 0;
    char split[20][20];

    char *context = NULL;
    char *token = strtok_r(msg, ":", &context);

    while (token != NULL) {
      strcpy(split[size], token);  // Copy to token list
      size++;
      token = strtok_r(NULL, ":", &context);
    }

    printf("%d", size);

    if (size == 4) {
      if (strcmp(split[0], "con") == 0) {
        log_info("dimensions: %sx%s\n", split[1], split[2]);
        // int pid = fork();

        // if (0 == pid) {
        //   char logfile[50];
        //   sprintf(logfile, "/usr/share/extd/%s.log", split[1]);

        //   char *const args[10] = {
        //       "/usr/bin/x11vnc", "-once", "-timeout", "30",
        //       "-localhost",     "-o",    logfile,    "-threads",
        //       "-passwd",         split[3]};

        //   execvp("/usr/bin/x11vnc", args);
        // }
      } else {
        log_error("invalid command: %s.", split[0]);
      }
    } else {
      log_error("invalid parameters received: %s", msg);
    }

    // printf("extd:success\n");
  }
}