#include <arpa/inet.h>
#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <unistd.h>

#include "config.h"
#include "util.c"

#define BUFLEN 512  // Max length of buffer

int in_file(char *needle, char *file) {
  size_t len = 0;
  ssize_t read;
  char *line = NULL;
  FILE *fp;
  int infile = 0;

  if ((fp = fopen(file, "r")) == NULL) {
    die("pid_file");
  }

  while ((read = getline(&line, &len, fp)) != -1) {
    line[strcspn(line, "\n")] = 0;

    if (!strcmp(line, needle)) {
      infile = 1;
      break;
    }
  }

  if (line) free(line);
  fclose(fp);

  return infile;
}

int main(int argc, char const *argv[]) {
  if (argc < 3) return die("invalid params");

  long port = strtol(argv[1], NULL, 10);
  const char *secret = argv[2];

  struct sockaddr_in si_me, si_other;

  int s, i, slen = sizeof(si_other), recv_len;
  char buf[BUFLEN];

  // create a UDP socket
  if ((s = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP)) == -1) {
    die("socket");
  }

  // zero out the structure
  memset((char *)&si_me, 0, sizeof(si_me));

  si_me.sin_family = AF_INET;
  si_me.sin_port = htons(port);
  si_me.sin_addr.s_addr = htonl(INADDR_ANY);

  // bind socket to port
  if (bind(s, (struct sockaddr *)&si_me, sizeof(si_me)) == -1) {
    die("bind");
  }

  // keep listening for data
  // printf("Waiting for data on port %d", port);

  while (1) {
    memset(buf, 0, strlen(buf));
    fflush(stdout);

    printf("waiting for connections...\n");
    if ((recv_len = recvfrom(s, buf, BUFLEN, 0, (struct sockaddr *)&si_other,
                             &slen)) == -1) {
      die("recvfrom()");
    }

    // print details of the client/peer and the data received
    printf("\nReceived packet from %s:%d\n", inet_ntoa(si_other.sin_addr),
           ntohs(si_other.sin_port));
    printf("Data: %s\n", buf);

    char *from = inet_ntoa(si_other.sin_addr);
    int size = 0;
    char split[20][20];

    char *context = NULL;
    char *token = strtok_r(buf, ":", &context);

    while (token != NULL) {
      strcpy(split[size], token);  // Copy to token list
      size++;
      token = strtok_r(NULL, ":", &context);
    }

    if (size == 2 && strcmp(split[0], secret) == 0) {
      printf("Data OK\n");

      int len = strlen(split[1]);
      if (split[1][len - 1] == '\n') split[1][len - 1] = '\0';

      char needle[255];
      sprintf(needle, "%s extd@%s", split[1], from);
      int infile = in_file(needle, AUTHORIZED_KEYS_FILE);

      if (infile == 0) {
        FILE *fp;
        char *file = AUTHORIZED_KEYS_FILE;

        if ((fp = fopen(file, "a")) == NULL) {
          die("pid_file");
        }

        if (fprintf(fp, "%s extd@%s\n", split[1], from) < 0) {
          die("fprintf");
        }

        fclose(fp);
      } else {
        printf("client was already in authorized_keys.\n");
      }

      printf("accepted.\n");
      if (sendto(s, buf, recv_len, 0, (struct sockaddr *)&si_other, slen) ==
          -1) {
        die("sendto()");
      }
    }
  }

  close(s);

  return 0;
}