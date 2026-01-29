# Deployment Guide

This guide covers deploying the Camera Relay Server to various cloud platforms.

## Table of Contents
- [Railway.app (Recommended)](#railwayapp-recommended)
- [Render.com](#rendercom)
- [Heroku](#heroku)
- [Google Cloud Run](#google-cloud-run)
- [AWS Elastic Beanstalk](#aws-elastic-beanstalk)
- [DigitalOcean App Platform](#digitalocean-app-platform)

---

## Railway.app (Recommended)

Railway is the easiest platform for deploying the relay server with automatic deployments.

### Prerequisites
- GitHub account
- Railway.app account (free tier available)

### Steps

1. **Create a Railway Account**
   - Go to [railway.app](https://railway.app)
   - Sign up with GitHub

2. **Create New Project**
   - Click "New Project"
   - Select "Deploy from GitHub repo"
   - Choose your `Camera-remote-control` repository

3. **Configure Build Settings**
   - Railway auto-detects Gradle projects
   - Go to Settings → Build
   - Set **Root Directory**: `relay-server`
   - Build command (auto-detected): `./gradlew shadowJar`
   - Start command: `java -jar build/libs/camera-relay-server.jar`

4. **Deploy**
   - Click "Deploy"
   - Railway will build and deploy automatically
   - You'll get a public URL like `https://your-app.railway.app`

5. **Configure Domain (Optional)**
   - Go to Settings → Domains
   - Add custom domain or use Railway's generated domain

### Environment Variables
Railway automatically sets `PORT` - no configuration needed.

### Cost
- Free tier: 500 hours/month, $5 credit
- After free tier: ~$5-10/month

---

## Render.com

Render provides free tier with automatic deployments.

### Prerequisites
- GitHub account
- Render.com account

### Steps

1. **Create Render Account**
   - Go to [render.com](https://render.com)
   - Sign up with GitHub

2. **Create New Web Service**
   - Click "New +" → "Web Service"
   - Connect your GitHub repository
   - Select `Camera-remote-control` repo

3. **Configure Service**
   - **Name**: `camera-relay-server`
   - **Root Directory**: `relay-server`
   - **Environment**: `Docker` or `Native`
   
   If using Native:
   - **Build Command**: `./gradlew shadowJar`
   - **Start Command**: `java -jar build/libs/camera-relay-server.jar`
   
   If using Docker:
   - Render auto-detects Dockerfile

4. **Plan**
   - Select "Free" tier (automatically sleeps after inactivity)
   - Or select "Starter" ($7/month) for always-on service

5. **Deploy**
   - Click "Create Web Service"
   - Wait for deployment to complete
   - Your URL: `https://camera-relay-server.onrender.com`

### Notes
- Free tier spins down after 15 minutes of inactivity
- First request after sleep takes ~30 seconds to wake up
- Starter plan stays always active

---

## Heroku

Classic platform with easy deployment using Git.

### Prerequisites
- Heroku account
- Heroku CLI installed

### Steps

1. **Install Heroku CLI**
   ```bash
   # macOS
   brew tap heroku/brew && brew install heroku
   
   # Ubuntu
   curl https://cli-assets.heroku.com/install.sh | sh
   
   # Windows
   # Download from https://devcenter.heroku.com/articles/heroku-cli
   ```

2. **Login to Heroku**
   ```bash
   heroku login
   ```

3. **Create Heroku App**
   ```bash
   heroku create your-camera-relay-server
   ```

4. **Deploy Using Git Subtree**
   ```bash
   # From repository root
   git subtree push --prefix relay-server heroku main
   ```
   
   Or set up a separate branch:
   ```bash
   git subtree split --prefix relay-server -b relay-server-deploy
   git push heroku relay-server-deploy:main
   ```

5. **Check Deployment**
   ```bash
   heroku open
   heroku logs --tail
   ```

### Alternative: GitHub Integration

1. In Heroku Dashboard, go to your app
2. Go to "Deploy" tab
3. Connect GitHub repository
4. Enable automatic deploys from main branch
5. Note: Heroku doesn't support monorepo subdirectory auto-deploy
   - You'll need buildpacks or separate repo

### Cost
- Eco Dynos: $5/month (can sleep)
- Basic: $7/month (always on)
- No free tier as of November 2022

---

## Google Cloud Run

Serverless container platform with generous free tier.

### Prerequisites
- Google Cloud account
- `gcloud` CLI installed
- Docker installed (or use Cloud Build)

### Steps

1. **Install gcloud CLI**
   ```bash
   # Follow instructions at:
   # https://cloud.google.com/sdk/docs/install
   ```

2. **Authenticate**
   ```bash
   gcloud auth login
   gcloud config set project YOUR_PROJECT_ID
   ```

3. **Enable APIs**
   ```bash
   gcloud services enable cloudbuild.googleapis.com
   gcloud services enable run.googleapis.com
   ```

4. **Deploy from Source**
   ```bash
   cd relay-server
   
   gcloud run deploy camera-relay-server \
     --source . \
     --platform managed \
     --region us-central1 \
     --allow-unauthenticated \
     --memory 512Mi
   ```

5. **Get URL**
   ```bash
   gcloud run services describe camera-relay-server \
     --region us-central1 \
     --format 'value(status.url)'
   ```

### Alternative: Deploy Pre-built Image

```bash
# Build locally
cd relay-server
docker build -t gcr.io/YOUR_PROJECT_ID/camera-relay-server .

# Push to Container Registry
docker push gcr.io/YOUR_PROJECT_ID/camera-relay-server

# Deploy
gcloud run deploy camera-relay-server \
  --image gcr.io/YOUR_PROJECT_ID/camera-relay-server \
  --platform managed \
  --region us-central1 \
  --allow-unauthenticated
```

### Cost
- Free tier: 2 million requests/month, 180,000 vCPU-seconds
- Pay-as-you-go after free tier
- Typically $0-5/month for light usage

---

## AWS Elastic Beanstalk

AWS platform for deploying applications.

### Prerequisites
- AWS account
- EB CLI or AWS Console access

### Using EB CLI

1. **Install EB CLI**
   ```bash
   pip install awsebcli
   ```

2. **Initialize**
   ```bash
   cd relay-server
   eb init -p docker camera-relay-server --region us-east-1
   ```

3. **Create Environment**
   ```bash
   eb create camera-relay-env
   ```

4. **Deploy**
   ```bash
   eb deploy
   ```

5. **Open App**
   ```bash
   eb open
   ```

### Using Console with Docker

1. Zip the relay-server directory
2. Go to Elastic Beanstalk console
3. Create new application
4. Choose "Docker" platform
5. Upload zip file
6. Configure instance (t2.micro for free tier)
7. Deploy

### Cost
- Free tier: t2.micro for 12 months
- After: ~$10-15/month for t2.micro

---

## DigitalOcean App Platform

Easy deployment with flat pricing.

### Steps

1. **Create DigitalOcean Account**
   - Go to [digitalocean.com](https://www.digitalocean.com)

2. **Create App**
   - Apps → Create App
   - Connect GitHub repository
   - Select `Camera-remote-control` repository

3. **Configure**
   - **Source Directory**: `relay-server`
   - **Type**: Dockerfile or use Buildpack (Gradle)
   - **Build Command**: `./gradlew shadowJar`
   - **Run Command**: `java -jar build/libs/camera-relay-server.jar`

4. **Select Plan**
   - Basic: $5/month (512MB RAM)
   - Professional: $12/month (1GB RAM)

5. **Deploy**
   - Click "Create Resources"
   - Wait for build and deployment

### Cost
- $5/month minimum (Basic plan)
- Includes custom domain, HTTPS, and auto-scaling

---

## Comparison Table

| Platform | Free Tier | Cost/Month | Auto Deploy | Docker | Sleeps? |
|----------|-----------|------------|-------------|---------|---------|
| Railway | 500 hrs | $5-10 | ✅ | ✅ | No |
| Render | Yes | Free/$7 | ✅ | ✅ | Yes (free) |
| Heroku | No | $5-7 | ⚠️ | ✅ | Optional |
| Cloud Run | 2M requests | $0-5 | ✅ | ✅ | Scales to 0 |
| AWS EB | 12 months | $10-15 | ✅ | ✅ | No |
| DigitalOcean | No | $5 | ✅ | ✅ | No |

## Recommendations

**Best for Quick Start**: Railway.app
- Easiest setup
- Auto-detects Gradle
- Good free tier

**Best for Always-On Free**: Google Cloud Run
- Generous free tier
- Scales automatically
- Serverless (pay per use)

**Best for Learning**: Render.com
- Free tier available
- Simple interface
- Good for testing

**Best for Production**: Google Cloud Run or DigitalOcean
- Reliable
- Good pricing
- Professional support

## Monitoring

After deployment, monitor your server:

```bash
# Check health
curl https://your-server-url.com/health

# Check status
curl https://your-server-url.com/

# Test WebSocket (using wscat)
wscat -c wss://your-server-url.com/camera-relay
```

## Troubleshooting

### Build Fails
- Ensure Java 17 is used
- Check Gradle wrapper is included
- Verify all dependencies are accessible

### Server Won't Start
- Check PORT environment variable is set
- Review logs for errors
- Ensure WebSocket support is enabled

### Connection Issues
- Verify WebSocket URL (ws:// or wss://)
- Check firewall rules
- Ensure server is accessible publicly

## Security Considerations

1. **HTTPS/WSS**: Always use secure connections in production
2. **Rate Limiting**: Consider adding rate limiting for production
3. **Authentication**: Current PIN system is basic - consider additional auth
4. **Monitoring**: Set up logging and monitoring
5. **Updates**: Keep dependencies updated

## Next Steps

After deployment:
1. Note your server URL
2. Test with wscat or similar tool
3. Update Android app with server URL
4. Test end-to-end camera control

For Android app integration, see the main project README.
