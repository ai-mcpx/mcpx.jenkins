#!/bin/bash

# Set variable
BASE_URL=https://mcpx.example.com

# Login registry
mcpx-cli --base-url=$BASE_URL login --method anonymous

# Publish server
mcpx-cli --base-url=$BASE_URL publish mcpx.json

# Query server
mcpx-cli --base-url=$BASE_URL servers
