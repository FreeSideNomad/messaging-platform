#!/usr/bin/env python3
"""
Script to fetch and display SonarQube issues sorted by priority.
Uses SONARQ_TOKEN environment variable for authentication.
"""

import os
import sys
import requests
from typing import List, Dict
from collections import defaultdict
from tabulate import tabulate


class SonarQubeClient:
    """Client for interacting with SonarQube API."""

    def __init__(self, base_url: str, token: str, project_key: str):
        self.base_url = base_url.rstrip('/')
        self.token = token
        self.project_key = project_key
        self.session = requests.Session()
        self.session.auth = (token, '')

    def get_issues(self, limit: int = 500) -> List[Dict]:
        """
        Fetch issues from SonarQube API.

        Args:
            limit: Maximum number of issues to fetch (default: 500)

        Returns:
            List of issue dictionaries
        """
        all_issues = []
        page = 1
        page_size = 100

        while len(all_issues) < limit:
            url = f"{self.base_url}/api/issues/search"
            params = {
                'componentKeys': self.project_key,
                'branch': 'main',
                'statuses': 'OPEN',
                'types': 'CODE_SMELL',
                'p': page,
                'ps': page_size,
            }

            try:
                response = self.session.get(url, params=params, timeout=10)
                response.raise_for_status()

                data = response.json()
                issues = data.get('issues', [])

                if not issues:
                    break

                all_issues.extend(issues)

                # Check if there are more pages
                if len(all_issues) >= data.get('total', 0):
                    break

                page += 1

            except requests.exceptions.RequestException as e:
                print(f"Error fetching issues: {e}")
                sys.exit(1)

        return all_issues[:limit]

    @staticmethod
    def get_priority_score(severity: str, type_: str) -> int:
        """
        Calculate priority score based on severity and type.
        Higher score = higher priority.

        Args:
            severity: Issue severity (BLOCKER, CRITICAL, MAJOR, MINOR, INFO)
            type_: Issue type (BUG, VULNERABILITY, CODE_SMELL, SECURITY_HOTSPOT)

        Returns:
            Priority score
        """
        severity_scores = {
            'BLOCKER': 5,
            'CRITICAL': 4,
            'MAJOR': 3,
            'MINOR': 2,
            'INFO': 1,
        }

        type_multiplier = {
            'VULNERABILITY': 1.5,
            'BUG': 1.3,
            'SECURITY_HOTSPOT': 1.2,
            'CODE_SMELL': 1.0,
        }

        severity_score = severity_scores.get(severity, 0)
        multiplier = type_multiplier.get(type_, 1.0)

        return severity_score * multiplier


def main():
    """Main function."""
    # Get configuration from environment
    token = os.environ.get('SONARQ_TOKEN')
    base_url = os.environ.get('SONARQ_BASE_URL', 'https://sonarcloud.io')
    project_key = os.environ.get('SONARQ_PROJECT_KEY', 'FreeSideNomad_messaging-platform')

    if not token:
        print("Error: SONARQ_TOKEN environment variable not set")
        sys.exit(1)

    print(f"Connecting to SonarQube at {base_url}")
    print(f"Project key: {project_key}")
    print()

    # Initialize client and fetch issues
    client = SonarQubeClient(base_url, token, project_key)

    print("Fetching issues...")
    issues = client.get_issues(limit=500)

    if not issues:
        print("No issues found.")
        return

    # Calculate priority scores and sort
    for issue in issues:
        priority_score = client.get_priority_score(issue['severity'], issue['type'])
        issue['priority_score'] = priority_score

    # Sort by priority score (descending) and then by severity
    issues.sort(key=lambda x: (-x['priority_score'],
                               ['BLOCKER', 'CRITICAL', 'MAJOR', 'MINOR', 'INFO'].index(x['severity'])))

    # Get top 50
    top_issues = issues[:50]

    # Prepare data for display
    table_data = []
    severity_counts = defaultdict(int)
    type_counts = defaultdict(int)

    for idx, issue in enumerate(top_issues, 1):
        severity = issue['severity']
        type_ = issue['type']
        severity_counts[severity] += 1
        type_counts[type_] += 1

        table_data.append([
            idx,
            severity,
            type_,
            issue.get('message', 'N/A')[:60],
            issue.get('component', 'N/A').split(':')[-1],
            issue.get('line', '-'),
        ])

    # Display results
    print(f"\n{'='*120}")
    print(f"TOP 50 SONARQUBE ISSUES (sorted by priority)")
    print(f"{'='*120}\n")

    headers = ['#', 'Severity', 'Type', 'Message', 'Component', 'Line']
    print(tabulate(table_data, headers=headers, tablefmt='grid'))

    # Summary statistics
    print(f"\n{'='*120}")
    print("SUMMARY")
    print(f"{'='*120}\n")

    print(f"Total issues fetched: {len(issues)}")
    print(f"Displayed (top 50): {len(top_issues)}\n")

    print("Issues by Severity:")
    for severity in ['BLOCKER', 'CRITICAL', 'MAJOR', 'MINOR', 'INFO']:
        count = severity_counts.get(severity, 0)
        if count > 0:
            print(f"  {severity}: {count}")

    print("\nIssues by Type:")
    for type_ in sorted(type_counts.keys()):
        print(f"  {type_}: {type_counts[type_]}")


if __name__ == '__main__':
    main()
