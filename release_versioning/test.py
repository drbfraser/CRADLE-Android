from google_auth_oauthlib.flow import InstalledAppFlow
from googleapiclient.discovery import build
import mimetypes

if __name__ == '__main__':
    flow = InstalledAppFlow.from_client_secrets_file(
        'client_secrets.json',
        scopes=['https://www.googleapis.com/auth/androidpublisher'],
        redirect_uri='urn:ietf:wg:oauth:2.0:oob'
    )
    # this gives a link and need to login through browser with 
    # icradle2020@gmail.com and then paste the token it gives back in the console
    flow.run_console()

    credentials = flow.credentials

    service = build('androidpublisher', 'v3', credentials=credentials)

    app_edit = service.edits().insert(packageName="com.cradleVSA.neptune").execute()

    mimetypes.add_type("application/octet-stream", ".aab")

    service.edits().bundles().upload(packageName="com.cradleVSA.neptune", editId=app_edit["id"], media_body="app_release.aab").execute()

    body = { # A track configuration. The resource for TracksService.
            "releases": [ # In a read request, represents all active releases in the track. In an update request, represents desired changes.
                { # A release within a track.
            "inAppUpdatePriority": 5,
            "releaseNotes": [ # A description of what is new in this release.
                { # Release notes specification, i.e. language and text.
                "language": "en-US", # Language localization code (a BCP-47 language tag; for example, "de-AT" for Austrian German).
                "text": "try uploading aab from API", # The text in the given language.
                },
            ],
            "status": "draft", # The status of the release.
            "versionCodes": ["36"],
            },
        ],
        "track": "internal", # Identifier of the track.
}