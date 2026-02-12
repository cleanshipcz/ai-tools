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
ARG OPENCLAW_IMAGE=alpine/openclaw:latest
FROM ${OPENCLAW_IMAGE}

# Optional: add custom npm tools later
# RUN npm -g install #   <tool1> #   <tool2>
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
    volumes:
      - /home/pi/openclaw-data/.openclaw:/home/node/.openclaw
    environment:
      - NODE_ENV=production
    security_opt:
      - no-new-privileges:true
    cap_drop:
      - ALL
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

Config persists via bind mount:

    /home/pi/openclaw-data/.openclaw

------------------------------------------------------------------------

## Security Properties Achieved

-   SSH key-only
-   No password login
-   Firewall restricts inbound to LAN SSH only
-   No privileged container
-   No Docker socket exposure
-   Git-versioned OpenClaw config
-   Container has no access to host SSH keys
