import sys
import socket
import json
import os
import hashlib
from StringIO import StringIO
from Crypto.Cipher import AES
from gzip import GzipFile

# TODO: dual-stack
UDP_IP = "0.0.0.0"
UDP_PORT = 23042

key = hashlib.sha256(b'm!ToSC]vb=:<b&XL.|Yq#LYE{V+$Mc~y').digest()
block_size = 16

sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
sock.bind((UDP_IP, UDP_PORT))

clientData = {}

while True:
	try:
		data, addr = sock.recvfrom(1500)
		print "packet from: " + addr[0] + ":" + str(addr[1])
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
