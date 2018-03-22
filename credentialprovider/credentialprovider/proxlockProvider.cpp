//
// THIS CODE AND INFORMATION IS PROVIDED "AS IS" WITHOUT WARRANTY OF
// ANY KIND, EITHER EXPRESSED OR IMPLIED, INCLUDING BUT NOT LIMITED TO
// THE IMPLIED WARRANTIES OF MERCHANTABILITY AND/OR FITNESS FOR A
// PARTICULAR PURPOSE.
//
// Copyright (c) Microsoft Corporation. All rights reserved.
//
// proxlockProvider implements ICredentialProvider, which is the main
// interface that logonUI uses to decide which tiles to display.
// In this sample, we will display one tile that uses each of the nine
// available UI controls.

#include <initguid.h>

#include "proxlockProvider.h"
#include "proxlockCredential.h"
#include "guid.h"
#include "bleWatcher.h"
#include "aes.h"


proxlockProvider::proxlockProvider():
    _cRef(1),
    _pCredential(nullptr),
    _pCredProviderUserArray(nullptr),
	_bleWatcher(nullptr)
{
    DllAddRef();
}

proxlockProvider::~proxlockProvider()
{
    if (_pCredential != nullptr)
    {
        _pCredential->Release();
        _pCredential = nullptr;
    }
    if (_pCredProviderUserArray != nullptr)
    {
        _pCredProviderUserArray->Release();
        _pCredProviderUserArray = nullptr;
    }

	if (_bleWatcher !=nullptr)
	{
		delete _bleWatcher;
	}

    DllRelease();
}

// This method acts as a callback for the hardware emulator. When it's called, it simply
// tells the infrastructure that it needs to re-enumerate the credentials.
void proxlockProvider::OnConnectStatusChanged()
{
	if (_bleWatcher != nullptr)
		if (_bleWatcher->GetConnectedStatus()) {
			delete _bleWatcher;

			string encryptedPass;
			ifstream inFile;
			string line;
			inFile.open("E:\proxlock.conf");
			while (getline(inFile, line)) {
				istringstream is_line(line);
				string key;
				if (getline(is_line, key, '=')) {
					string value;
					if (getline(is_line, value)) {
						if (key == "Password")
							encryptedPass = value;
					}
				}

			}
			inFile.close();
			//wchar_t* password = const_cast<wchar_t*>(encrypted.c_str());
			
			AES decryptor;
			decryptor.SetParameters(128);
			string aesKey = "D4A9C8DC8DB79DD6B0DE415EC71254EB";
			unsigned char* btKey = new unsigned char[(aesKey.length() - 1) / 2]();
			for (int i = 0; i < aesKey.length(); i += 2) {
				string hexString = aesKey.substr(i, 2);
				btKey[i / 2] = (unsigned char)strtol(hexString.c_str(), NULL, 16);

			}
			
			/*wchar_t str[255];
			_stprintf(str, _T("length is %d"), encryptedPass.length());
			MessageBox(0, str, 0, MB_OK);*/
			unsigned char* encryptedPassbytearray = new unsigned char[encryptedPass.length() / 2]();
			for (int i = 0; i < encryptedPass.length(); i += 2) {
				string hexString = encryptedPass.substr(i, 2);
				encryptedPassbytearray[i / 2] = (unsigned char)strtol(hexString.c_str(), NULL, 16);

			}
			decryptor.StartDecryption(btKey);
			unsigned char* decryptedData = new unsigned char[(encryptedPass.length() / 2) + 1]();
			decryptor.Decrypt(encryptedPassbytearray, decryptedData, encryptedPass.length() / 32);
			char* message = new char[(encryptedPass.length() / 2)];
			wchar_t* mywcstring;
			if (decryptedData[(encryptedPass.length() / 2) - 1] < 17) {
				int lastChar = 0;
				for (int i = 0; i < encryptedPass.length() / 2 - decryptedData[(encryptedPass.length() / 2) - 1]; i++) {
					message[i] = decryptedData[i];
					lastChar++;
				}
				message[lastChar + 1] = '\0';
				mywcstring = new wchar_t(lastChar+1);

				//MessageBoxA(0, message, 0, MB_OK);
			}
				
			size_t origsize = strlen(message);
			size_t convertedChars = 0;
			
			mbstowcs_s(&convertedChars, mywcstring, origsize, message, _TRUNCATE);
			//mywcstring[newsize - 1] = L"\0";
			
			
			
			PWSTR password = mywcstring;
			/*wchar_t str[255];
			_stprintf(str, _T("chars are\n%d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d\n%d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d"), wcstring[0], wcstring[1], wcstring[2], wcstring[3], wcstring[4], wcstring[5], wcstring[6], wcstring[7], wcstring[8], wcstring[9], wcstring[10], wcstring[11], wcstring[12], wcstring[13], wcstring[14], wcstring[15], wcstring[16], 
				mywcstring[0], mywcstring[1], mywcstring[2], mywcstring[3], mywcstring[4], mywcstring[5], mywcstring[6], mywcstring[7], mywcstring[8], mywcstring[9], mywcstring[10], mywcstring[11], mywcstring[12], mywcstring[13], mywcstring[14], mywcstring[15], mywcstring[16]);
			MessageBox(0, str, 0, MB_OK);*/
			//PWSTR password = wcstring;
			/*TCHAR str[255];
			_stprintf(str, _T("seconds is %d"), sizeof(password));
			MessageBox(0, str, 0, MB_OK);*/
			
				

			_pCredential->_rgFieldStrings[SFI_PASSWORD] = password;
		}
			
	if (_pcpe != nullptr)
	{
		HRESULT hr;
		
		hr = _pcpe->CredentialsChanged(_upAdviseContext);
	}
}

// SetUsageScenario is the provider's cue that it's going to be asked for tiles
// in a subsequent call.
HRESULT proxlockProvider::SetUsageScenario(
    CREDENTIAL_PROVIDER_USAGE_SCENARIO cpus,
    DWORD /*dwFlags*/)
{
    HRESULT hr;

    // Decide which scenarios to support here. Returning E_NOTIMPL simply tells the caller
    // that we're not designed for that scenario.
    switch (cpus)
    {
    case CPUS_LOGON:
    case CPUS_UNLOCK_WORKSTATION:
        // The reason why we need _fRecreateEnumeratedCredentials is because ICredentialProviderSetUserArray::SetUserArray() is called after ICredentialProvider::SetUsageScenario(),
        // while we need the ICredentialProviderUserArray during enumeration in ICredentialProvider::GetCredentialCount()
        _cpus = cpus;
        _fRecreateEnumeratedCredentials = true;
        hr = S_OK;
        break;

    case CPUS_CHANGE_PASSWORD:
    case CPUS_CREDUI:
        hr = E_NOTIMPL;
        break;

    default:
        hr = E_INVALIDARG;
        break;
    }

    return hr;
}

// SetSerialization takes the kind of buffer that you would normally return to LogonUI for
// an authentication attempt.  It's the opposite of ICredentialProviderCredential::GetSerialization.
// GetSerialization is implement by a credential and serializes that credential.  Instead,
// SetSerialization takes the serialization and uses it to create a tile.
//
// SetSerialization is called for two main scenarios.  The first scenario is in the credui case
// where it is prepopulating a tile with credentials that the user chose to store in the OS.
// The second situation is in a remote logon case where the remote client may wish to
// prepopulate a tile with a username, or in some cases, completely populate the tile and
// use it to logon without showing any UI.
//
// If you wish to see an example of SetSerialization, please see either the SampleCredentialProvider
// sample or the SampleCredUICredentialProvider sample.  [The logonUI team says, "The original sample that
// this was built on top of didn't have SetSerialization.  And when we decided SetSerialization was
// important enough to have in the sample, it ended up being a non-trivial amount of work to integrate
// it into the main sample.  We felt it was more important to get these samples out to you quickly than to
// hold them in order to do the work to integrate the SetSerialization changes from SampleCredentialProvider
// into this sample.]
HRESULT proxlockProvider::SetSerialization(
    _In_ CREDENTIAL_PROVIDER_CREDENTIAL_SERIALIZATION const *pcpcs)
{
    return E_NOTIMPL;
}

// Called by LogonUI to give you a callback.  Providers often use the callback if they
// some event would cause them to need to change the set of tiles that they enumerated.
HRESULT proxlockProvider::Advise(
    _In_ ICredentialProviderEvents *pcpe,
    _In_ UINT_PTR upAdviseContext)
{

	/*if (_pcpe != nullptr)
	{
		_pcpe->Release();
	}*/
	_pcpe = pcpe;
	_pcpe->AddRef();
	_upAdviseContext = upAdviseContext;
	return S_OK;
	//return E_NOTIMPL;
}

// Called by LogonUI when the ICredentialProviderEvents callback is no longer valid.
HRESULT proxlockProvider::UnAdvise()
{

	if (_pcpe != NULL)
	{
		_pcpe->Release();
		_pcpe = NULL;
	}
	return S_OK;
}

// Called by LogonUI to determine the number of fields in your tiles.  This
// does mean that all your tiles must have the same number of fields.
// This number must include both visible and invisible fields. If you want a tile
// to have different fields from the other tiles you enumerate for a given usage
// scenario you must include them all in this count and then hide/show them as desired
// using the field descriptors.
HRESULT proxlockProvider::GetFieldDescriptorCount(
    _Out_ DWORD *pdwCount)
{
    *pdwCount = SFI_NUM_FIELDS;
    return S_OK;
}

// Gets the field descriptor for a particular field.
HRESULT proxlockProvider::GetFieldDescriptorAt(
    DWORD dwIndex,
    _Outptr_result_nullonfailure_ CREDENTIAL_PROVIDER_FIELD_DESCRIPTOR **ppcpfd)
{
    HRESULT hr;
    *ppcpfd = nullptr;

    // Verify dwIndex is a valid field.
    if ((dwIndex < SFI_NUM_FIELDS) && ppcpfd)
    {
        hr = FieldDescriptorCoAllocCopy(s_rgCredProvFieldDescriptors[dwIndex], ppcpfd);
    }
    else
    {
        hr = E_INVALIDARG;
    }

    return hr;
}

// Sets pdwCount to the number of tiles that we wish to show at this time.
// Sets pdwDefault to the index of the tile which should be used as the default.
// The default tile is the tile which will be shown in the zoomed view by default. If
// more than one provider specifies a default the last used cred prov gets to pick
// the default. If *pbAutoLogonWithDefault is TRUE, LogonUI will immediately call
// GetSerialization on the credential you've specified as the default and will submit
// that credential for authentication without showing any further UI.
HRESULT proxlockProvider::GetCredentialCount(
    _Out_ DWORD *pdwCount,
    _Out_ DWORD *pdwDefault,
    _Out_ BOOL *pbAutoLogonWithDefault)
{
	*pbAutoLogonWithDefault = FALSE;
    *pdwDefault = 0;
	if(_bleWatcher != nullptr)
		if (_bleWatcher->GetConnectedStatus())
			*pbAutoLogonWithDefault = TRUE;
		
    if (_fRecreateEnumeratedCredentials)
    {
        _fRecreateEnumeratedCredentials = false;
        _ReleaseEnumeratedCredentials();
        _CreateEnumeratedCredentials();
    }

    *pdwCount = 1;

    return S_OK;
}

// Returns the credential at the index specified by dwIndex. This function is called by logonUI to enumerate
// the tiles.
HRESULT proxlockProvider::GetCredentialAt(
    DWORD dwIndex,
    _Outptr_result_nullonfailure_ ICredentialProviderCredential **ppcpc)
{
    HRESULT hr = E_INVALIDARG;
    *ppcpc = nullptr;

    if ((dwIndex == 0) && ppcpc)
    {
        hr = _pCredential->QueryInterface(IID_PPV_ARGS(ppcpc));
    }
    return hr;
}

// This function will be called by LogonUI after SetUsageScenario succeeds.
// Sets the User Array with the list of users to be enumerated on the logon screen.
HRESULT proxlockProvider::SetUserArray(_In_ ICredentialProviderUserArray *users)
{
    if (_pCredProviderUserArray)
    {
        _pCredProviderUserArray->Release();
    }
    _pCredProviderUserArray = users;
    _pCredProviderUserArray->AddRef();
    return S_OK;
}

void proxlockProvider::_CreateEnumeratedCredentials()
{
    switch (_cpus)
    {
    case CPUS_LOGON:
    case CPUS_UNLOCK_WORKSTATION:
        {
            _EnumerateCredentials();
            break;
        }
    default:
        break;
    }
}

void proxlockProvider::_ReleaseEnumeratedCredentials()
{
    if (_pCredential != nullptr)
    {
        _pCredential->Release();
        _pCredential = nullptr;
    }
}

HRESULT proxlockProvider::_EnumerateCredentials()
{
    HRESULT hr = E_UNEXPECTED;


    if (_pCredProviderUserArray != nullptr)
    {
		
        DWORD dwUserCount;
        _pCredProviderUserArray->GetCount(&dwUserCount);
        if (dwUserCount > 0)
        {
            ICredentialProviderUser *pCredUser;
            hr = _pCredProviderUserArray->GetAt(0, &pCredUser);
            if (SUCCEEDED(hr))
            {
				if (!_bleWatcher)
				{

					_bleWatcher = new BLEWatcher();
					if (_bleWatcher != NULL)
					{
						// Initialize each of the object we've just created. 
						// - The CCommandWindow needs a pointer to us so it can let us know 
						// when to re-enumerate credentials.
						// - The proxlockCredential needs field descriptors.
						// - The CMessageCredential needs field descriptors and a message.
						hr = _bleWatcher->Initialize(this);
						if (SUCCEEDED(hr))
						{

							_pCredential = new(std::nothrow) proxlockCredential();
							if (_pCredential != nullptr)
							{
								hr = _pCredential->Initialize(_cpus, s_rgCredProvFieldDescriptors, s_rgFieldStatePairs, pCredUser);
								if (FAILED(hr))
								{
									_pCredential->Release();
									_pCredential = nullptr;
								}
							}
							else
							{
								hr = E_OUTOFMEMORY;
							}
							pCredUser->Release();
						}
					}
					else
					{
						hr = E_OUTOFMEMORY;
					}
				}
            }
        }
    }
	if (FAILED(hr))
	{
		if (_bleWatcher != nullptr)
		{
			delete _bleWatcher;
			_bleWatcher = nullptr;
		}
		if (_pCredential != nullptr)
		{
			_pCredential->Release();
			_pCredential = nullptr;
		}

	}
    return hr;
}

// Boilerplate code to create our provider.
HRESULT CSample_CreateInstance(_In_ REFIID riid, _Outptr_ void **ppv)
{
    HRESULT hr;
    proxlockProvider *pProvider = new(std::nothrow) proxlockProvider();
    if (pProvider)
    {
        hr = pProvider->QueryInterface(riid, ppv);
        pProvider->Release();
    }
    else
    {
        hr = E_OUTOFMEMORY;
    }
    return hr;
}
