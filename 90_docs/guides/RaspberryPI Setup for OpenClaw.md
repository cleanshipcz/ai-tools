# Raspberry Pi 4 -- Secure OpenClaw Docker Sandbox Setup

Target: Clean Raspberry Pi OS (64-bit), LAN-only, SSH key-only,
Dockerized OpenClaw with persistent config and Git-managed state.

------------------------------------------------------------------------

## 1. Base System Preparation

``` bash
sudo apt update && sudo apt full-upgrade -y
sudo reboot
```

------------------------------------------------------------------------

## 2. SSH Hardening

Edit:

``` bash
sudo nano /etc/ssh/sshd_config
```

Ensure:

    PasswordAuthentication no
    PermitRootLogin no
    PubkeyAuthentication yes

Restart:

``` bash
sudo systemctl restart ssh
```

------------------------------------------------------------------------

## 3. Firewall (LAN-only SSH)

``` bash
sudo apt install -y ufw
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow from 192.168.0.0/24 to any port 22
sudo ufw enable
sudo ufw status
```

------------------------------------------------------------------------

## 4. Install Docker

``` bash
sudo apt install -y docker.io
sudo usermod -aG docker pi
```

Log out and back in, then test:

``` bash
docker run hello-world
```

------------------------------------------------------------------------

## 5. Install Docker Compose

``` bash
sudo apt install -y docker-compose
```

------------------------------------------------------------------------

## 6. Git Setup for OpenClaw Config

Generate SSH key:

``` bash
ssh-keygen -t ed25519
```

Add `~/.ssh/id_ed25519.pub` to GitHub.

Clone repo:

``` bash
cd ~
git clone git@github.com:<you>/<repo>.git openclaw-data
```

Expected structure:

    /home/pi/openclaw-data/.openclaw

------------------------------------------------------------------------

## 7. Project Structure

``` bash
mkdir -p ~/openclaw
cd ~/openclaw
```

------------------------------------------------------------------------

## 8. Custom Dockerfile

Create `~/openclaw/Dockerfile`:

``` dockerfile
ARG OPENCLAW_IMAGE=ghcr.io/openclaw/openclaw:latest
FROM ${OPENCLAW_IMAGE}

USER root

# Install utilities
RUN apt-get update && \
    apt-get install -y \
      nano \
      ripgrep \
      wkhtmltopdf \
      gh \
      poppler-utils \
      jq \
      ffmpeg \
    && rm -rf /var/lib/apt/lists/*

RUN curl -L https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp -o /usr/local/bin/yt-dlp \
      && chmod a+rx /usr/local/bin/yt-dlp

# Add aliases
RUN echo "alias ll='ls -la'" >> /etc/bash.bashrc
RUN echo "alias claudedanger='claude --dangerously-skip-permissions'" >> /etc/bash.bashrc

# Make openclaw CLI available on PATH
RUN ln -s /app/openclaw.mjs /usr/local/bin/openclaw

# Install tools
RUN npm -g install \
   clawhub \
   blogwatcher \
   @google/gemini-cli

USER node

RUN curl -fsSL https://claude.ai/install.sh | bash
ENV PATH="/home/node/.local/bin:${PATH}"
```

------------------------------------------------------------------------

## 9. Docker Compose Configuration

Create `~/openclaw/compose.yaml`:

``` yaml
services:
  openclaw:
    build: .
    container_name: openclaw
    restart: unless-stopped
    init: true
    volumes:
      - /home/pi/openclaw-data/.openclaw:/home/node/.openclaw
      - /home/pi/openclaw-data/secrets:/run/secrets:ro
      - /home/pi/.claude:/home/node/.claude
      - /home/pi/.claude.json:/home/node/.claude.json
    ports:
      - "127.0.0.1:18789:18789"
    environment:
      - NODE_ENV=production
      - HOME=/home/node
      - NODE_OPTIONS=--max-old-space-size=1536
    security_opt:
      - no-new-privileges:true
    cap_drop:
      - ALL
    deploy:
      resources:
        limits:
          memory: 4g
    healthcheck:
      test: ["CMD", "node", "-e", "fetch('http://localhost:18789/health').then(r => process.exit(r.ok ? 0 : 1)).catch(() => process.exit(1))"]
      interval: 60s
      timeout: 10s
      retries: 3
      start_period: 30s
```

------------------------------------------------------------------------

## 10. Build and Start

``` bash
docker compose build
docker compose up -d
docker compose ps
docker logs openclaw
```

------------------------------------------------------------------------

## 11. Updating OpenClaw

``` bash
cd ~/openclaw
docker compose build
docker compose up -d
```

## 12. Activate Web GUI



## 13. Install Tailscale (optional, for remote access + Web UI)

Register at https://tailscale.com/ and create a tailnet. Then, on the Raspberry Pi, run:

Install

```bash
curl -fsSL https://tailscale.com/install.sh | sh
```

Start and authenticate

```bash
sudo tailscale up
```

Expose the gateway via Tailscale Serve

```bash
sudo tailscale serve --bg 18789
```

Check status

```bash
tailscale status
```

After Tailscale, access Web UI at: https://raspberrypi.<your-tailnet>.ts.net

### Note

Devices need to be approved ->
- Open the Tailscale URL on the target device, then run on host:
  - `tailscale status` -> get the RPI IP, hostname (second column)
  - `tailscale status --json | grep -i 'MagicDNSSuffix'` -> get the MagicDNS suffix (e.g. `tailac590e.ts.net`)
  - full URL is: `http://<hostname>.<MagicDNSSuffix>:18789` (e.g. `http://raspberrypi.tailac590e.ts.net:18789`)
- then run in the container:
  - `openclaw devices list` -> get the device ID
  - `openclaw devices approve <request-id>` -> approve the device (by request ID, not device ID)

------------------------------------------------------------------------

## Security Properties Achieved

- SSH key-only
- No password login
- Firewall restricts inbound to LAN SSH only
- No privileged container
- No Docker socket exposure
- Git-versioned OpenClaw config
- Container has no access to host SSH keys
