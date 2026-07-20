JAVAC := javac
JAVA := java

SRC_DIR := src
BUILD_DIR := build/classes

CLIENT_MAIN := Client
SERVER_MAIN := Server

.PHONY: all client server run-client run-server clean

all: client server

client: $(BUILD_DIR)/$(CLIENT_MAIN).class

server: $(BUILD_DIR)/$(SERVER_MAIN).class

$(BUILD_DIR):
	mkdir -p $(BUILD_DIR)

$(BUILD_DIR)/$(CLIENT_MAIN).class: $(SRC_DIR)/Client.java $(SRC_DIR)/FileCache.java | $(BUILD_DIR)
	$(JAVAC) -d $(BUILD_DIR) $(SRC_DIR)/Client.java $(SRC_DIR)/FileCache.java

$(BUILD_DIR)/$(SERVER_MAIN).class: $(SRC_DIR)/Server.java | $(BUILD_DIR)
	$(JAVAC) -d $(BUILD_DIR) $(SRC_DIR)/Server.java

run-client: client
	$(JAVA) -cp $(BUILD_DIR) $(CLIENT_MAIN)

run-server: server
	$(JAVA) -cp $(BUILD_DIR) $(SERVER_MAIN)

clean:
	rm -rf build
