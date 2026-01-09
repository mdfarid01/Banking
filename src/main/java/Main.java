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

    if (args.length != 2 || !args[0].equals("--resolver")) {
      throw new RuntimeException("Usage: --resolver <ip:port>");
    }

    String[] resolverParts = args[1].split(":");
    InetAddress resolverIp = InetAddress.getByName(resolverParts[0]);
    int resolverPort = Integer.parseInt(resolverParts[1]);

    DatagramSocket serverSocket = new DatagramSocket(2053);
    DatagramSocket resolverSocket = new DatagramSocket();
    resolverSocket.setSoTimeout(3000);

    while (true) {
      byte[] req = new byte[512];
      DatagramPacket packet = new DatagramPacket(req, req.length);
      serverSocket.receive(packet);

      int requestId =
          ((req[0] & 0xFF) << 8) | (req[1] & 0xFF);
      int flags =
          ((req[2] & 0xFF) << 8) | (req[3] & 0xFF);
      int opcode = (flags >> 11) & 0xF;
      int rd = (flags >> 8) & 1;

      int qdcount =
          ((req[4] & 0xFF) << 8) | (req[5] & 0xFF);

      int offset = 12;
      List<Question> questions = new ArrayList<>();

      for (int i = 0; i < qdcount; i++) {
        NameResult nr = readName(req, offset);
        offset = nr.nextOffset;

        int qtype =
            ((req[offset] & 0xFF) << 8) | (req[offset + 1] & 0xFF);
        offset += 2;

        int qclass =
            ((req[offset] & 0xFF) << 8) | (req[offset + 1] & 0xFF);
        offset += 2;

        Question q = new Question();
        q.qname = nr.name;
        q.qtype = qtype;
        q.qclass = qclass;
        questions.add(q);
      }

      List<byte[]> answers = new ArrayList<>();

      // Forward each question separately
      for (Question q : questions) {
        byte[] fwd = new byte[512];
        int fo = 0;

        // ID (any)
        fwd[fo++] = 0x12;
        fwd[fo++] = 0x34;

        int fFlags = (opcode << 11) | (rd << 8);
        fwd[fo++] = (byte) (fFlags >> 8);
        fwd[fo++] = (byte) fFlags;

        fwd[fo++] = 0x00;
        fwd[fo++] = 0x01; // QDCOUNT = 1
        fwd[fo++] = 0x00;
        fwd[fo++] = 0x00;
        fwd[fo++] = 0x00;
        fwd[fo++] = 0x00;
        fwd[fo++] = 0x00;
        fwd[fo++] = 0x00;

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

        // skip question
        NameResult skip = readName(resp, ansOffset);
        ansOffset = skip.nextOffset + 4;

        // copy answer RR
        int answerLen = respPacket.getLength() - ansOffset;
        byte[] ans = new byte[answerLen];
        System.arraycopy(resp, ansOffset, ans, 0, answerLen);
        answers.add(ans);
      }

      // Build final response
      byte[] res = new byte[512];
      int ro = 0;

      res[ro++] = (byte) (requestId >> 8);
      res[ro++] = (byte) requestId;

      int rFlags = (1 << 15) | (opcode << 11) | (rd << 8);
      res[ro++] = (byte) (rFlags >> 8);
      res[ro++] = (byte) rFlags;

      res[ro++] = 0x00;
      res[ro++] = (byte) questions.size();

      res[ro++] = 0x00;
      res[ro++] = (byte) answers.size();

      res[ro++] = 0x00;
      res[ro++] = 0x00;
      res[ro++] = 0x00;
      res[ro++] = 0x00;

      for (Question q : questions) {
        System.arraycopy(q.qname, 0, res, ro, q.qname.length);
        ro += q.qname.length;
        res[ro++] = 0x00;
        res[ro++] = 0x01;
        res[ro++] = 0x00;
        res[ro++] = 0x01;
      }

      for (byte[] a : answers) {
        System.arraycopy(a, 0, res, ro, a.length);
        ro += a.length;
      }

      serverSocket.send(
          new DatagramPacket(res, ro, packet.getSocketAddress()));
    }
  }
}
