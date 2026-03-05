package ru.yakut54.ktoto.data.api

import com.google.gson.Gson
import retrofit2.HttpException
import ru.yakut54.ktoto.data.model.ErrorResponse
import java.io.IOException

fun Throwable.toRussianMessage(): String = when (this) {
    is HttpException -> {
        val raw = response()?.errorBody()?.string()
        val parsed = runCatching {
            Gson().fromJson(raw, ErrorResponse::class.java)?.error
        }.getOrNull()

        when {
            parsed?.contains("already exists", ignoreCase = true) == true ->
                "Пользователь с таким именем или email уже существует"
            parsed?.contains("credentials", ignoreCase = true) == true ->
                "Неверный логин или пароль"
            parsed?.contains("not found", ignoreCase = true) == true ->
                "Пользователь не найден"
            parsed?.contains("Unauthorized", ignoreCase = true) == true ->
                "Необходимо войти в аккаунт"
            code() == 400 -> "Проверьте правильность введённых данных"
            code() == 401 -> "Неверный логин или пароль"
            code() == 403 -> "Нет доступа"
            code() == 409 -> "Пользователь с таким именем уже существует"
            code() == 500 -> "Ошибка сервера, попробуйте позже"
            else -> "Ошибка сети (${code()})"
        }
    }
    is IOException -> "Нет подключения к интернету"
    else -> "Что-то пошло не так"
}
