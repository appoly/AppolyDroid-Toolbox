---
type: "query"
date: "2026-06-09T09:16:54.735072+00:00"
question: "How do UploadResult and APIResult relate? Should they converge or is the decoupling deliberate?"
contributor: "graphify"
source_nodes: ["UploadResult", "APIResult", "DirectUploadResult", "GenericBaseRepo.uploadFileToS3"]
---

# Q: How do UploadResult and APIResult relate? Should they converge or is the decoupling deliberate?

## Answer

Deliberate decoupling — do NOT converge. S3Uploader is standalone (no BaseRepo dependency), so its UploadResult/DirectUploadResult types cannot be APIResult without forcing a BaseRepo dependency on standalone consumers. APIResult is also richer (responseCode, isNetworkError(), contracts, map/onSuccess/onError) — an S3 failure is not an HTTP-API failure. The conversion UploadResult->APIResult already exists in the OPTIONAL integration module BaseRepo-S3Uploader (GenericBaseRepo.uploadFileToS3), which is the correct place for it (dependency inversion via opt-in glue). The only mild near-duplication worth review is UploadResult vs DirectUploadResult within S3Uploader (differ only in whether Success carries a filePath).

## Source Nodes

- UploadResult
- APIResult
- DirectUploadResult
- GenericBaseRepo.uploadFileToS3