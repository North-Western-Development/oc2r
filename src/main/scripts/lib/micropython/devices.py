import io
import os
import select
import json


class Device:
    def __init__(self, device_bus, device_id):
        self.bus = device_bus
        self.device_id = device_id
        self._methods = None

    @property
    def methods(self):
        if self._methods is None:
            self._methods = self.bus.methods(self.device_id)
        return self._methods

    def __getattr__(self, item):
        return lambda *args: self.bus.invoke(self.device_id, item, *args)

    def __str__(self):
        doc = ""
        for method in self.methods:
            doc += method["name"] + "("
            if "parameters" in method:
                i = 0
                for p in method["parameters"]:
                    if i > 0:
                        doc += ", "
                    if "name" in p:
                        doc += p["name"]
                    else:
                        doc += "arg" + str(i)
                    if "type" in p:
                        doc += ": " + p["type"]
                    i += 1
            doc += ")"
            if "returnType" in method:
                doc += ": " + method["returnType"]
            doc += "\n"

            if "description" in method and method["description"]:
                doc += method["description"] + "\n"

            if "parameters" in method:
                i = 0
                for p in method["parameters"]:
                    if "description" in p:
                        doc += "  "
                        if "name" in p:
                            doc += p["name"]
                        else:
                            doc += "args" + str(i)
                        doc += "  " + p["description"] + "\n"
                    i += 1
        return doc


class DeviceBus:
    MESSAGE_DELIMITER = b'\0'

    def __init__(self, path):
        self.file = io.open(path, "+b")
        os.system("stty -F %s raw -echo" % path)
        self.poll = select.poll()
        self.poll.register(self.file.fileno(), select.POLLIN)
        self._clear_buffer()

    def close(self):
        self.file.close()
        self._clear_buffer()

    def flush(self):
        self._clear_buffer()
        self._skip_input()

    def list(self):
        self.flush()
        self._write_message({'type': "list"})
        return self._read_message("list")

    def get(self, device_id):
        for device in self.list():
            if device["deviceId"] == device_id:
                return Device(self, device["deviceId"])
        return None

    def find(self, type_name):
        for device in self.list():
            if "typeNames" in device and type_name in device["typeNames"]:
                return Device(self, device["deviceId"])
        return None

    def find_all(self, type_name):
        found_devices = []
        for device in self.list():
            if "typeNames" in device and type_name in device["typeNames"]:
                found_devices.append(Device(self, device["deviceId"]))
        return found_devices

    def methods(self, device_id):
        self.flush()
        self._write_message({"type": "methods", "data": device_id})
        return self._read_message("methods")

    def invoke(self, device_id, method_name, *args):
        self.flush()
        self._write_message({"type": "invoke", "data": {
            "deviceId": device_id,
            "name": method_name,
            "parameters": args
        }})
        return self._read_message("result")

    def _write_message(self, data):
        self.file.write(self.MESSAGE_DELIMITER + json.dumps(data) + self.MESSAGE_DELIMITER)

    def _read_message(self, expected_type):
        '''Read a message from the bus, blocking if necessary

        @param expected_type: The type of message we expect to see (eg. results)
        '''

        message = b""

        # Skip leading delimiters
        while True:
            if self._buffer_remaining() == 0:
                self._fill_buffer()

            while self._buffer_remaining() and self._buffer[self._buffer_pos] in self.MESSAGE_DELIMITER:
                self._buffer_pos += 1

            if self._buffer_remaining():
                break

        # Rest of the buffer should have at least one non-delim byte
        # Read full message
        while (next_delim_pos := self._buffer.find(self.MESSAGE_DELIMITER, self._buffer_pos)) == -1:
            message += self._read_buffer()
            self._fill_buffer()

        message += self._read_buffer(next_delim_pos)

        # parse message
        data = json.loads(message)
        if data["type"] == expected_type:
            if "data" in data:
                return data["data"]
            else:
                return
        elif data["type"] == "error":
            raise Exception(data["data"])
        else:
            raise Exception("unexpected message type: %s" % data["type"])

    def _buffer_remaining(self):
        return len(self._buffer) - self._buffer_pos

    def _read_buffer(self, end=None):
        if end is None:
            end = len(self._buffer)

        old_pos = self._buffer_pos
        self._buffer_pos = end
        return self._buffer[old_pos:end]

    def _clear_buffer(self):
        self._buffer = bytearray()
        self._buffer_pos = 0

    def _fill_buffer(self):
        assert self._buffer_remaining() == 0
        self.poll.poll()  # Blocking wait until we have some data.
        self._buffer = self._read(1024)
        self._buffer_pos = 0

    def _read(self, limit):
        # This is horrible, but don't know how to know how many bytes are available,
        # so reading one by one is necessary to avoid blocking.
        data = bytearray()
        bytesRead = 0
        while bytesRead < limit and len(self.poll.poll(0)) > 0:
            data.extend(self.file.read(1))
            bytesRead += 1
        return data

    def _skip_input(self):
        # This is horrible, but don't know how to know how many bytes are available,
        # so reading one by one is necessary to avoid blocking.
        while len(self.poll.poll(0)) > 0:
            self.file.read(1)


def bus():
    return DeviceBus(os.getenv("OC2R_BUS_PATH", "/dev/hvc0"))
