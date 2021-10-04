#include <arpa/inet.h>
#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <unistd.h>

#include "config.h"

#define BUFLEN 512  // Max length of buffer

int die(char *s) {
  perror(s);
  exit(1);
  return 1;
}

char **str_split(char *a_str, const char a_delim) {
  char **result = 0;
  size_t count = 0;
  char *tmp = a_str;
  char *last_comma = 0;
  char delim[2];
  delim[0] = a_delim;
  delim[1] = 0;

  /* Count how many elements will be extracted. */
  while (*tmp) {
    if (a_delim == *tmp) {
      count++;
      last_comma = tmp;
    }
    tmp++;
  }

  /* Add space for trailing token. */
  count += last_comma < (a_str + strlen(a_str) - 1);

  /* Add space for terminating null string so caller
     knows where the list of returned strings ends. */
  count++;

  result = malloc(sizeof(char *) * count);

  if (result) {
    size_t idx = 0;
    char *token = strtok(a_str, delim);

    while (token) {
      assert(idx < count);
      *(result + idx++) = strdup(token);
      token = strtok(0, delim);
    }
    assert(idx == count - 1);
    *(result + idx) = 0;
  }

  return result;
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

    if ((recv_len = recvfrom(s, buf, BUFLEN, 0, (struct sockaddr *)&si_other,
                             &slen)) == -1) {
      die("recvfrom()");
    }

    // print details of the client/peer and the data received
    printf("\nReceived packet from %s:%d\n", inet_ntoa(si_other.sin_addr),
           ntohs(si_other.sin_port));
    printf("Data: %s\n", buf);

    char *from = inet_ntoa(si_other.sin_addr);
    char **split = str_split(buf, ':');
    int size = (sizeof split / sizeof *split) + 1;

    if (size == 2 && strcmp(split[0], secret) == 0) {
      printf("Data OK");

      FILE *fp;
      char *file = AUTHORIZED_KEYS_FILE;

      if ((fp = fopen(file, "a")) == NULL) {
        die("pid_file");
      }

      int len = strlen(split[1]);
      if (split[1][len - 1] == '\n') split[1][len - 1] = '\0';

      if (fprintf(fp, "ssh-rsa %s extd@%s", split[1], from) < 0) {
        die("fprintf");
      }

      fclose(fp);

      if (sendto(s, buf, recv_len, 0, (struct sockaddr *)&si_other, slen) ==
          -1) {
        die("sendto()");
      }
    }
  }

  close(s);

  return 0;
}