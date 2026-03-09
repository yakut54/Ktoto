import {
  CreateBucketCommand,
  DeleteObjectsCommand,
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
  const internalEndpoint = process.env.MINIO_ENDPOINT ?? 'http://minio:9000'
  // Public endpoint is used in presigned URLs so Android/clients can download files
  const publicEndpoint = process.env.MINIO_PUBLIC_ENDPOINT ?? internalEndpoint
  app.log.info(`[S3] internalEndpoint=${internalEndpoint}  publicEndpoint=${publicEndpoint}`)

  const credentials = {
    accessKeyId: process.env.MINIO_ROOT_USER ?? 'ktoto',
    secretAccessKey: process.env.MINIO_ROOT_PASSWORD ?? 'ktoto-minio-2026',
  }
  const clientOpts = {
    region: 'us-east-1',
    credentials,
    forcePathStyle: true,
    requestChecksumCalculation: 'WHEN_REQUIRED' as const,
    responseChecksumValidation: 'WHEN_REQUIRED' as const,
  }

  // Internal client — used for upload (talks to minio container directly)
  const client = new S3Client({ ...clientOpts, endpoint: internalEndpoint })

  // Public client — used for presigned URLs (signs with public host so signature is valid for clients)
  const publicClient = new S3Client({ ...clientOpts, endpoint: publicEndpoint })

  // Ensure bucket exists (retry up to 5 times — MinIO may not be ready instantly)
  for (let attempt = 1; attempt <= 5; attempt++) {
    try {
      await client.send(new HeadBucketCommand({ Bucket: BUCKET }))
      app.log.info(`S3 bucket "${BUCKET}" exists`)
      break
    } catch (err: unknown) {
      const code = (err as { Code?: string; name?: string })?.Code ?? (err as { name?: string })?.name
      if (code === 'NoSuchBucket' || code === 'NotFound' || code === '404') {
        try {
          await client.send(new CreateBucketCommand({ Bucket: BUCKET }))
          app.log.info(`S3 bucket "${BUCKET}" created`)
          break
        } catch (createErr) {
          app.log.warn({ createErr }, 'Failed to create S3 bucket')
        }
      } else if (attempt < 5) {
        app.log.warn({ err, attempt }, 'S3 not ready, retrying in 2s...')
        await new Promise(r => setTimeout(r, 2000))
      } else {
        app.log.error({ err }, 'S3 bucket check failed after 5 attempts — continuing without guarantee')
      }
    }
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
    // Sign with publicClient — signature includes public host, so URL is valid for clients
    const url = await getSignedUrl(
      publicClient,
      new GetObjectCommand({ Bucket: BUCKET, Key: key }),
      { expiresIn: ttlSeconds },
    )
    app.log.info(`[S3] presignedUrl key=${key}  url=${url.substring(0, 100)}...`)
    return url
  }

  async function deleteObjects(keys: string[]): Promise<void> {
    if (!keys.length) return
    await client.send(
      new DeleteObjectsCommand({
        Bucket: BUCKET,
        Delete: { Objects: keys.map((Key) => ({ Key })), Quiet: true },
      }),
    )
  }

  app.decorate('s3', { upload, presignedUrl, deleteObjects })
})

declare module 'fastify' {
  interface FastifyInstance {
    s3: {
      upload(key: string, stream: Readable | Buffer, mimeType: string): Promise<void>
      presignedUrl(key: string, ttlSeconds?: number): Promise<string>
      deleteObjects(keys: string[]): Promise<void>
    }
  }
}
