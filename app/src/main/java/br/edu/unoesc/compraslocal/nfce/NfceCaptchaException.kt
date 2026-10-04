package br.edu.unoesc.compraslocal.nfce

class NfceCaptchaException(
    message: String = "A SEFAZ/SC exige verificação (captcha). Use a consulta no app abaixo.",
) : Exception(message)
