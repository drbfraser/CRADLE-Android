from google_auth_oauthlib.flow import InstalledAppFlow
from googleapiclient.discovery import build
import argparse
import os.path


#Define command line arguments
TRACK_CMD_LINE = 'track'
UPDATE_PRIORITY_CMD_LINE = 'update_priority'
#Define Package Name
PACKAGE_NAME = "com.cradleplatform.neptune"


def parseCommandLineArgs():
    scriptDescription = (
    'update inAppUpdatePriority for releases\n'
    'script requires client_secrets.json to be in the same directory which can be found at:\n'
    'https://console.cloud.google.com/apis/api/androidpublisher.googleapis.com/credentials?project=cradle-vsa\n'
    'login with icradle2020@gmail.com'
    )
    parser = argparse.ArgumentParser(description=scriptDescription, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument(f'--{TRACK_CMD_LINE}', help='The track the release is on one of [beta, alpha, internal, production]')
    parser.add_argument(f'--{UPDATE_PRIORITY_CMD_LINE}', help='The update priority for a release, a value from 0-5')
    return vars(parser.parse_args())


def checkClientSecretsExists():
    fileExists = os.path.isfile(r'./client_secrets.json')
    if not fileExists:
        print('client_secrets.json does not exist in the directory, please download it at:')
        print('https://console.cloud.google.com/apis/api/androidpublisher.googleapis.com/credentials?project=cradle-vsa')
        quit()


def authorizeAndGetService():
    flow = InstalledAppFlow.from_client_secrets_file(
        'client_secrets.json',
        scopes=['https://www.googleapis.com/auth/androidpublisher'],
        redirect_uri='urn:ietf:wg:oauth:2.0:oob'
    )
    # this gives a link and need to login through browser with 
    # icradle2020@gmail.com and then paste the token it gives back in the console
    flow.run_console()

    credentials = flow.credentials
    
    return build('androidpublisher', 'v3', credentials=credentials)


if __name__ == '__main__':
    args = parseCommandLineArgs()
    trackToUpdate = args[TRACK_CMD_LINE]
    updatePriority = args[UPDATE_PRIORITY_CMD_LINE]

    checkClientSecretsExists()

    service = authorizeAndGetService()

    try:
        app_edit = service.edits().insert(packageName=PACKAGE_NAME).execute()

        service.edits().tracks().list(packageName=PACKAGE_NAME, editId=app_edit["id"]).execute()

        # get a particular track
        track = service.edits().tracks().get(packageName=PACKAGE_NAME, editId=app_edit.get("id"), track=trackToUpdate).execute()

        # there's a releases field; see this link 
        # https://googleapis.github.io/google-api-python-client/docs/dyn/androidpublisher_v3.edits.tracks.html 
        # for info about what's in a release object
        print(track)

        # set the priority for the first release in the list
        track["releases"][0]["inAppUpdatePriority"] = updatePriority
        print(track)

        # update and commit
        service.edits().tracks().update(packageName=PACKAGE_NAME, editId=app_edit["id"], track=trackToUpdate, body=track).execute()
        service.edits().commit(packageName=PACKAGE_NAME, editId=app_edit["id"]).execute()

    except(KeyboardInterrupt):
        pass

    finally:
        service.close()
    