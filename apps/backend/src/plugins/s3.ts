import {
  CreateBucketCommand,
  HeadBucketCommand,
  PutObjectCommand,
  S3Client,
} from '@aws-sdk/client-s3'
import { getSignedUrl } from '@aws-sdk/s3-request-presigner'
import { GetObjectCommand } from '@aws-sdk/client-s3'
import fp from 'fastify-plugin'
import type { FastifyInstance } from 'fastify'
import { Readable } from 'stream'

const BUCKET = 'ktoto-media'

export const s3Plugin = fp(async (app: FastifyInstance) => {
  const client = new S3Client({
    endpoint: process.env.MINIO_ENDPOINT ?? 'http://minio:9000',
    region: 'us-east-1',
    credentials: {
      accessKeyId: process.env.MINIO_ROOT_USER ?? 'ktoto',
      secretAccessKey: process.env.MINIO_ROOT_PASSWORD ?? 'ktoto-minio-2026',
    },
    forcePathStyle: true, // required for MinIO
  })

  // Ensure bucket exists
  try {
    await client.send(new HeadBucketCommand({ Bucket: BUCKET }))
  } catch {
    await client.send(new CreateBucketCommand({ Bucket: BUCKET }))
    app.log.info(`S3 bucket "${BUCKET}" created`)
  }

  async function upload(key: string, stream: Readable | Buffer, mimeType: string): Promise<void> {
    await client.send(
      new PutObjectCommand({
        Bucket: BUCKET,
        Key: key,
        Body: stream,
        ContentType: mimeType,
      }),
    )
  }

  async function presignedUrl(key: string, ttlSeconds = 3600): Promise<string> {
    return getSignedUrl(
      client,
      new GetObjectCommand({ Bucket: BUCKET, Key: key }),
      { expiresIn: ttlSeconds },
    )
  }

  app.decorate('s3', { upload, presignedUrl })
})

declare module 'fastify' {
  interface FastifyInstance {
    s3: {
      upload(key: string, stream: Readable | Buffer, mimeType: string): Promise<void>
      presignedUrl(key: string, ttlSeconds?: number): Promise<string>
    }
  }
}
