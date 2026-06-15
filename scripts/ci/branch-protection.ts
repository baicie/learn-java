#!/usr/bin/env node

const token = process.env.GITHUB_TOKEN ?? process.env.GH_TOKEN;
const repository = process.env.GITHUB_REPOSITORY;
const branches = (process.env.BRANCH_PROTECTION_BRANCHES ?? "main,develop")
  .split(",")
  .map((branch) => branch.trim())
  .filter(Boolean);

const requiredChecks = (
  process.env.BRANCH_PROTECTION_CHECKS ?? "Docs,Backend,Frontend,CI Summary"
)
  .split(",")
  .map((check) => check.trim())
  .filter(Boolean);

if (!token) {
  throw new Error(
    "GITHUB_TOKEN or GH_TOKEN is required and must have repository administration permission.",
  );
}

if (!repository || !repository.includes("/")) {
  throw new Error("GITHUB_REPOSITORY must be set as owner/repo.");
}

for (const branch of branches) {
  const url = `https://api.github.com/repos/${repository}/branches/${encodeURIComponent(
    branch,
  )}/protection`;

  const response = await fetch(url, {
    method: "PUT",
    headers: {
      Accept: "application/vnd.github+json",
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
      "X-GitHub-Api-Version": "2022-11-28",
    },
    body: JSON.stringify({
      required_status_checks: {
        strict: true,
        contexts: requiredChecks,
      },
      enforce_admins: true,
      required_pull_request_reviews: {
        dismiss_stale_reviews: true,
        require_code_owner_reviews: true,
        required_approving_review_count: 1,
        require_last_push_approval: false,
      },
      restrictions: null,
      required_linear_history: true,
      allow_force_pushes: false,
      allow_deletions: false,
      block_creations: false,
      required_conversation_resolution: true,
      lock_branch: false,
      allow_fork_syncing: true,
    }),
  });

  if (!response.ok) {
    const body = await response.text();
    throw new Error(
      `Failed to configure ${repository}:${branch}: ${response.status} ${body}`,
    );
  }

  console.log(`Configured branch protection for ${repository}:${branch}`);
}
