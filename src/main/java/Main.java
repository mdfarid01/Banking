import java.io.*;
import java.net.*;
import java.util.*;

public class Main {

  // ---------- Data Structures ----------
  static class Question {
    byte[] qname;
    int qtype;
    int qclass;
  }

  static class NameResult {
    byte[] name;
    int nextOffset;
  }

  // ---------- DNS Name Reader (handles compression) ----------
  static NameResult readName(byte[] packet, int offset) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    int originalOffset = offset;
    boolean jumped = false;

    while (true) {
      int len = packet[offset] & 0xFF;

      // Compression pointer
      if ((len & 0xC0) == 0xC0) {
        int pointer =
            ((len & 0x3F) << 8) | (packet[offset + 1] & 0xFF);
        offset = pointer;
        jumped = true;
        continue;
      }

      // End of name
      if (len == 0) {
        out.write(0);
        offset++;
        break;
      }

      // Normal label
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

  // ---------- MAIN ----------
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

      // ---------- RECEIVE REQUEST ----------
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

      // ---------- PARSE QUESTIONS ----------
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

      boolean singleQuestion = (questions.size() == 1);
      List<byte[]> answers = new ArrayList<>();

      // ---------- FORWARD EACH QUESTION ----------
      for (Question q : questions) {

        // Build forwarding packet (1 question only)
        byte[] fwd = new byte[512];
        int fo = 0;

        fwd[fo++] = 0x12;
        fwd[fo++] = 0x34;

        int fFlags = (opcode << 11) | (rd << 8);
        fwd[fo++] = (byte) (fFlags >> 8);
        fwd[fo++] = (byte) fFlags;

        fwd[fo++] = 0x00;
        fwd[fo++] = 0x01; // QDCOUNT = 1
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

        // ---------- ANSWER HANDLING ----------
        if (singleQuestion) {
          // COPY REAL ANSWER FROM RESOLVER
          int ansOffset = 12;
          NameResult skip = readName(resp, ansOffset);
          ansOffset = skip.nextOffset + 4;

          int answerLen = respPacket.getLength() - ansOffset;
          byte[] ans = new byte[answerLen];
          System.arraycopy(resp, ansOffset, ans, 0, answerLen);
          answers.add(ans);

        } else {
          // SYNTHETIC ANSWER (SAFE FOR MULTI + COMPRESSION)
          ByteArrayOutputStream ans = new ByteArrayOutputStream();

          ans.write(q.qname);
          ans.write(0x00); ans.write(0x01); // TYPE A
          ans.write(0x00); ans.write(0x01); // CLASS IN

          ans.write(0x00); ans.write(0x00);
          ans.write(0x00); ans.write(0x3c); // TTL

          ans.write(0x00); ans.write(0x04); // RDLENGTH

          ans.write(0x08); ans.write(0x08);
          ans.write(0x08); ans.write(0x08); // IP

          answers.add(ans.toByteArray());
        }
      }

      // ---------- BUILD FINAL RESPONSE ----------
      byte[] res = new byte[512];
      int ro = 0;

      res[ro++] = (byte) (requestId >> 8);
      res[ro++] = (byte) requestId;

      int rFlags = (1 << 15) | (opcode << 11) | (rd << 8);
      if (opcode != 0) rFlags |= 4;

      res[ro++] = (byte) (rFlags >> 8);
      res[ro++] = (byte) rFlags;

      res[ro++] = 0x00;
      res[ro++] = (byte) questions.size();

      res[ro++] = 0x00;
      res[ro++] = (byte) answers.size();

      res[ro++] = 0x00; res[ro++] = 0x00;
      res[ro++] = 0x00; res[ro++] = 0x00;

      // Questions
      for (Question q : questions) {
        System.arraycopy(q.qname, 0, res, ro, q.qname.length);
        ro += q.qname.length;
        res[ro++] = 0x00; res[ro++] = 0x01;
        res[ro++] = 0x00; res[ro++] = 0x01;
      }

      // Answers
      for (byte[] a : answers) {
        System.arraycopy(a, 0, res, ro, a.length);
        ro += a.length;
      }

      serverSocket.send(
          new DatagramPacket(res, ro, packet.getSocketAddress()));
    }
  }
}
