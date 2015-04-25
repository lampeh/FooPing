import sys
import socket
import json
import os
import hashlib
import hmac
from StringIO import StringIO
from Crypto.Cipher import AES
from gzip import GzipFile

# TODO: dual-stack
UDP_IP = "0.0.0.0"
UDP_PORT = 23042

key = hashlib.sha256(b'm!ToSC]vb=:<b&XL.|Yq#LYE{V+$Mc~y').digest()
block_size = 16

mac_key = hashlib.sha256(b'sM[N9+l8~N7Ox_7^EI>s|vLkiVXo-[T').digest()
mac_size = 20

def compare_digest(x, y):
	if not (isinstance(x, bytes) and isinstance(y, bytes)):
		raise TypeError("both inputs should be instances of bytes")
	if len(x) != len(y):
		return False
	result = 0
	for a, b in zip(x, y):
		result |= ord(a) ^ ord(b)
	return result == 0


sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
sock.bind((UDP_IP, UDP_PORT))

clientData = {}

while True:
	try:
		raw, addr = sock.recvfrom(1500)
		print "packet from: " + addr[0] + ":" + str(addr[1])

		data = raw[:-mac_size]

		mac = raw[-mac_size:]
		mac2 = hmac.new(mac_key, data, hashlib.sha1).digest()
		if (not compare_digest(mac, mac2)):
			print "MAC mismatch!"
			continue

		msg = GzipFile(fileobj = StringIO(AES.new(key, AES.MODE_CFB, data[:block_size]).decrypt(data[block_size:]))).read()
		print msg
		packet = json.loads(msg)
		pingData = {}
		pingData.update(packet[0])
		client = pingData["client"]
		if client in clientData:
			clientData[client].update(pingData)
		else:
			clientData[client] = pingData
		f = open('data.json.tmp', 'w')
		f.write(json.dumps([clientData]))
		f.close()
		os.rename('data.json.tmp', 'data.json');
	except Exception:
		print "exception in packet from: " + addr[0] + ":" + str(addr[1])
		print sys.exc_info()
		pass
