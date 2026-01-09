import java.io.*;
import java.net.*;
import java.util.*;

public class Main {

  static class Question {
    byte[] qname;
    int qtype;
    int qclass;
  }

  static class NameResult {
    byte[] name;
    int nextOffset;
  }

  // DNS name reader (supports compression)
  static NameResult readName(byte[] packet, int offset) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    int originalOffset = offset;
    boolean jumped = false;

    while (true) {
      int len = packet[offset] & 0xFF;

      if ((len & 0xC0) == 0xC0) {
        int pointer =
            ((len & 0x3F) << 8) | (packet[offset + 1] & 0xFF);
        offset = pointer;
        jumped = true;
        continue;
      }

      if (len == 0) {
        out.write(0);
        offset++;
        break;
      }

      out.write(len);
      offset++;
      out.write(packet, offset, len);
      offset += len;
    }

    NameResult res = new NameResult();
    res.name = out.toByteArray();
    res.nextOffset = jumped ? originalOffset + 2 : offset;
    return res;
  }

  public static void main(String[] args) throws Exception {

    boolean forwardingEnabled = false;
    InetAddress resolverIp = null;
    int resolverPort = 0;

    if (args.length == 2 && args[0].equals("--resolver")) {
      forwardingEnabled = true;
      String[] parts = args[1].split(":");
      resolverIp = InetAddress.getByName(parts[0]);
      resolverPort = Integer.parseInt(parts[1]);
    }

    DatagramSocket serverSocket = new DatagramSocket(2053);

    DatagramSocket resolverSocket = null;
    if (forwardingEnabled) {
      resolverSocket = new DatagramSocket();
      resolverSocket.setSoTimeout(1500); // important
    }

    while (true) {

      byte[] request = new byte[512];
      DatagramPacket packet = new DatagramPacket(request, request.length);
      serverSocket.receive(packet);

      int requestId =
          ((request[0] & 0xFF) << 8) | (request[1] & 0xFF);
      int flags =
          ((request[2] & 0xFF) << 8) | (request[3] & 0xFF);
      int opcode = (flags >> 11) & 0xF;
      int rd = (flags >> 8) & 1;

      int qdcount =
          ((request[4] & 0xFF) << 8) | (request[5] & 0xFF);

      int offset = 12;
      List<Question> questions = new ArrayList<>();

      for (int i = 0; i < qdcount; i++) {
        NameResult nr = readName(request, offset);
        offset = nr.nextOffset;

        int qtype =
            ((request[offset] & 0xFF) << 8) | (request[offset + 1] & 0xFF);
        offset += 2;

        int qclass =
            ((request[offset] & 0xFF) << 8) | (request[offset + 1] & 0xFF);
        offset += 2;

        Question q = new Question();
        q.qname = nr.name;
        q.qtype = qtype;
        q.qclass = qclass;
        questions.add(q);
      }

      boolean singleQuestion = (questions.size() == 1);
      List<byte[]> answers = new ArrayList<>();

      for (Question q : questions) {

        boolean forwarded = false;

        if (forwardingEnabled && singleQuestion) {
          try {
            byte[] fwd = new byte[512];
            int fo = 0;

            fwd[fo++] = 0x12;
            fwd[fo++] = 0x34;

            int fFlags = (opcode << 11) | (rd << 8);
            fwd[fo++] = (byte) (fFlags >> 8);
            fwd[fo++] = (byte) fFlags;

            fwd[fo++] = 0x00; fwd[fo++] = 0x01;
            fwd[fo++] = 0x00; fwd[fo++] = 0x00;
            fwd[fo++] = 0x00; fwd[fo++] = 0x00;
            fwd[fo++] = 0x00; fwd[fo++] = 0x00;

            System.arraycopy(q.qname, 0, fwd, fo, q.qname.length);
            fo += q.qname.length;

            fwd[fo++] = (byte) (q.qtype >> 8);
            fwd[fo++] = (byte) q.qtype;
            fwd[fo++] = (byte) (q.qclass >> 8);
            fwd[fo++] = (byte) q.qclass;

            resolverSocket.send(
                new DatagramPacket(fwd, fo, resolverIp, resolverPort));

            byte[] resp = new byte[512];
            DatagramPacket respPacket =
                new DatagramPacket(resp, resp.length);
            resolverSocket.receive(respPacket);

            int ansOffset = 12;
            NameResult skip = readName(resp, ansOffset);
            ansOffset = skip.nextOffset + 4;

            int ansLen = respPacket.getLength() - ansOffset;
            if (ansLen > 0) {
              byte[] ans = new byte[ansLen];
              System.arraycopy(resp, ansOffset, ans, 0, ansLen);
              answers.add(ans);
              forwarded = true;
            }
          } catch (Exception ignored) {
            // fallback to synthetic
          }
        }

        if (!forwarded) {
          ByteArrayOutputStream ans = new ByteArrayOutputStream();
          ans.write(q.qname);
          ans.write(0x00); ans.write(0x01);
          ans.write(0x00); ans.write(0x01);
          ans.write(0x00); ans.write(0x00);
          ans.write(0x00); ans.write(0x3c);
          ans.write(0x00); ans.write(0x04);
          ans.write(0x08); ans.write(0x08);
          ans.write(0x08); ans.write(0x08);
          answers.add(ans.toByteArray());
        }
      }

      byte[] response = new byte[512];
      int ro = 0;

      response[ro++] = (byte) (requestId >> 8);
      response[ro++] = (byte) requestId;

      int rFlags = (1 << 15) | (opcode << 11) | (rd << 8);
      if (opcode != 0) rFlags |= 4;

      response[ro++] = (byte) (rFlags >> 8);
      response[ro++] = (byte) rFlags;

      response[ro++] = 0x00;
      response[ro++] = (byte) questions.size();
      response[ro++] = 0x00;
      response[ro++] = (byte) answers.size();
      response[ro++] = 0x00; response[ro++] = 0x00;
      response[ro++] = 0x00; response[ro++] = 0x00;

      for (Question q : questions) {
        System.arraycopy(q.qname, 0, response, ro, q.qname.length);
        ro += q.qname.length;
        response[ro++] = 0x00; response[ro++] = 0x01;
        response[ro++] = 0x00; response[ro++] = 0x01;
      }

      for (byte[] a : answers) {
        System.arraycopy(a, 0, response, ro, a.length);
        ro += a.length;
      }

      serverSocket.send(
          new DatagramPacket(response, ro, packet.getSocketAddress()));
    }
  }
}
